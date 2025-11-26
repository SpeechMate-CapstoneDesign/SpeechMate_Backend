import cv2
import mediapipe as mp
import time
import math
import numpy as np
from collections import deque


class GestureDetector:
    def __init__(self, fps=30):
        """
        비디오 분석 전용 제스처 감지기

        Args:
            fps: 비디오 FPS (프레임 기반 임계값 계산에 사용)
        """
        # MediaPipe 초기화
        self.mp_holistic = mp.solutions.holistic
        self.mp_face_mesh = mp.solutions.face_mesh

        # Holistic 모델 (포즈, 손 감지)
        self.holistic = self.mp_holistic.Holistic(
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
            model_complexity=1
        )

        # FaceMesh 모델 (얼굴, 눈 깜빡임)
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )

        self.fps = fps
        self._init_parameters()
        self._init_state_variables()
        self._init_counters()

    def _init_parameters(self):
        """파라미터 설정"""
        # 공통
        self.VISIBILITY_THRESHOLD = 0.3

        # === Head Pose ===
        self.NPR_CEILING_THRESHOLD = 0.25
        self.NPR_FLOOR_THRESHOLD = 0.44
        self.POSE_FRAME_COUNT = int(self.fps * 0.8)

        # === Lip Bite ===
        self.LIP_BITE_RATIO_THRESHOLD = 0.25
        self.LIP_BITE_FRAME_COUNT = int(self.fps * 0.5)

        # === Hand Near Face ===
        self.HAND_NEAR_FACE_THRESHOLD = 0.15
        self.HAND_GESTURE_FRAME_COUNT = int(self.fps * 0.5)

        # === Slant ===
        self.SLANT_RATIO_THRESHOLD = 0.03
        self.SLANT_FRAME_COUNT = int(self.fps * 1)

        # === Rigid Attention ===
        self.RIGID_FRAME_COUNT = max(self.fps * 2, 60)
        self.ATTENTION_DIST_RATIO = 0.5
        self.RIGID_MOVEMENT_RATIO = 0.02

        # === Blink (빈도 기반) ===
        self.EAR_THRESHOLD = 0.23
        self.BLINK_FRAME_COUNT = max(int(self.fps * 0.05), 2)
        self.FREQUENT_BLINK_WINDOW = int(self.fps * 10)
        self.FREQUENT_BLINK_THRESHOLD = 15

        # === Head Shake ===
        self.HEAD_SHAKE_THRESHOLD = 0.08
        self.HEAD_SHAKE_FRAME_COUNT = 10

        # === Arms Crossed ===
        self.ARMS_CROSSED_FRAME_COUNT = int(self.fps * 0.3)
        self.ARMS_CROSSED_ELBOW_VISIBILITY = 0.5

        # === Hands Behind Back ===
        self.HANDS_BEHIND_BACK_FRAME_COUNT = int(self.fps * 0.5)

        # === Hand Rubbing ===
        self.HAND_RUBBING_DISTANCE = 0.25
        self.HAND_RUBBING_MOVEMENT = 0.015
        self.HAND_RUBBING_FRAME_COUNT = 20

        # === Fig Leaf ===
        self.FIG_LEAF_HANDS_CLOSE_THRESHOLD = 150
        self.FIG_LEAF_FRAME_COUNT = int(self.fps * 1.0)

    def _init_state_variables(self):
        """상태 변수 초기화"""
        # Head Pose
        self.pose_status = "Forward"
        self.pose_counter = 0
        self.last_pose_event = ""

        # Lip Bite
        self.lip_bite_status = False
        self.lip_bite_counter = 0
        self.last_lip_bite_event = False

        # Hand Near Face
        self.hand_near_face_status = False
        self.hand_near_face_counter = 0
        self.last_hand_near_face_event = False

        # Slant
        self.slant_status = False
        self.slant_counter = 0
        self.last_slant_event = False

        # Rigid
        self.rigid_status = False
        self.rigid_counter = 0
        self.last_rigid_event = False

        # Blink
        self.blink_status_closed = False
        self.blink_counter = 0
        self.blink_timestamps = deque(maxlen=50)
        self.last_frequent_blink_event = False

        # Head Shake
        self.last_head_shake_event = False

        # Arms Crossed
        self.arms_crossed_status = False
        self.arms_crossed_counter = 0
        self.last_arms_crossed_event = False

        # Hands Behind Back
        self.hands_behind_back_status = False
        self.hands_behind_back_counter = 0
        self.last_hands_behind_back_event = False

        # Hand Rubbing
        self.last_hand_rubbing_event = False

        # Hand to Face Region
        self.last_hand_to_face_event = None

        # Fig Leaf
        self.fig_leaf_status = False
        self.fig_leaf_counter = 0
        self.last_fig_leaf_event = False

        # 히스토리
        self.left_wrist_history = deque(maxlen=self.RIGID_FRAME_COUNT)
        self.right_wrist_history = deque(maxlen=self.RIGID_FRAME_COUNT)
        self.left_ear_positions = deque(maxlen=self.HEAD_SHAKE_FRAME_COUNT)
        self.right_ear_positions = deque(maxlen=self.HEAD_SHAKE_FRAME_COUNT)
        self.rubbing_history = deque(maxlen=30)
        self.hand_face_distances = deque(maxlen=15)

    def _init_counters(self):
        """이벤트 카운터 초기화"""
        self.total_head_bow_events = 0
        self.total_ceiling_events = 0
        self.total_lip_bite_events = 0
        self.total_hand_near_face_events = 0
        self.total_slant_events = 0
        self.total_rigid_events = 0
        self.total_blink_events = 0
        self.total_head_shake_events = 0
        self.total_arms_crossed_events = 0
        self.total_hands_behind_back_events = 0
        self.total_hand_rubbing_events = 0
        self.total_hand_to_face_events = {}
        self.total_fig_leaf_events = 0

        # 타임라인 추적
        self.timeline = {
            '고개숙이기': [],
            '천장보기': [],
            '입술깨물기': [],
            '눈깜빡임': [],
            '고개흔들기': [],
            '비스듬한자세': [],
            '경직된차려': [],
            '팔짱끼기': [],
            '뒷짐': [],
            '손비비기': [],
            '무화과잎자세': [],
            '머리터치': [],
            '이마터치': [],
            '코터치': [],
            '입술터치': [],
            '턱터치': [],
            '왼쪽귀터치': [],
            '오른쪽귀터치': []
        }

        self.gesture_log = []
        self.start_time = time.time()

    def get_distance_2d(self, lm1, lm2, w, h):
        """2D 픽셀 거리 계산"""
        x1, y1 = int(lm1.x * w), int(lm1.y * h)
        x2, y2 = int(lm2.x * w), int(lm2.y * h)
        return math.hypot(x1 - x2, y1 - y2)

    def _format_time(self, ms):
        seconds = int(ms / 1000)
        m, s = divmod(seconds, 60)
        h, m = divmod(m, 60)
        return f"{h:02d}:{m:02d}:{s:02d}"

    def process_frame(self, image, timestamp_ms):
        """한 프레임 처리 (시각화 없음)"""
        height, width, _ = image.shape
        imgRGB = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

        # MediaPipe 실행
        results_holistic = self.holistic.process(imgRGB)
        results_facemesh = self.face_mesh.process(imgRGB)

        # 현재 프레임에서 새로 발생한 이벤트
        new_events = []

        # === A. 얼굴 분석 ===
        if results_facemesh.multi_face_landmarks:
            face_events = self._process_face(
                results_facemesh.multi_face_landmarks[0],
                width, height,
                timestamp_ms
            )
            new_events.extend(face_events)

        # === B. 포즈 분석 ===
        if results_holistic.pose_landmarks:
            pose_events = self._process_pose(
                results_holistic.pose_landmarks,
                width, height,
                timestamp_ms
            )
            new_events.extend(pose_events)

        # === C. 손 분석 ===
        if results_facemesh.multi_face_landmarks and (
                results_holistic.left_hand_landmarks or results_holistic.right_hand_landmarks):
            hand_events = self._process_hands(
                results_holistic,
                results_facemesh.multi_face_landmarks[0],
                width, height,
                timestamp_ms
            )
            new_events.extend(hand_events)

        # 시각화 없이 원본 이미지 반환
        return image, new_events

    def _process_face(self, face_landmarks, width, height, timestamp_ms):
        """얼굴 분석"""
        events = []
        landmarks = face_landmarks.landmark
        time_str = self._format_time(timestamp_ms)

        try:
            # === 1. Head Pose ===
            current_pose_direction = "Forward"

            eye_center_y = landmarks[6].y
            nose_tip_y = landmarks[1].y
            chin_tip_y = landmarks[152].y
            face_vertical_height = chin_tip_y - eye_center_y

            if face_vertical_height > 0.01:
                avg_npr = (nose_tip_y - eye_center_y) / face_vertical_height

                if avg_npr > self.NPR_FLOOR_THRESHOLD:
                    current_pose_direction = "HeadBow"
                elif avg_npr < self.NPR_CEILING_THRESHOLD:
                    current_pose_direction = "Ceiling"

            if current_pose_direction != "Forward":
                if current_pose_direction == self.pose_status:
                    self.pose_counter += 1
                else:
                    self.pose_status = current_pose_direction
                    self.pose_counter = 1
                    self.last_pose_event = ""

                if self.pose_counter >= self.POSE_FRAME_COUNT and self.last_pose_event != self.pose_status:
                    self.last_pose_event = self.pose_status
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))

                    if self.pose_status == "HeadBow":
                        self.total_head_bow_events += 1
                        self.timeline['고개숙이기'].append(time_str)
                        events.append("고개숙이기")
                    elif self.pose_status == "Ceiling":
                        self.total_ceiling_events += 1
                        self.timeline['천장보기'].append(time_str)
                        events.append("천장보기")
            else:
                if self.last_pose_event != "":
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))

                    if self.last_pose_event == "HeadBow":
                        events.append("고개숙이기 해제")
                    elif self.last_pose_event == "Ceiling":
                        events.append("천장보기 해제")

                self.pose_counter = 0
                self.pose_status = "Forward"
                self.last_pose_event = ""

            # === 2. Lip Bite ===
            upper_outer_lip_y = landmarks[12].y
            upper_inner_lip_y = landmarks[13].y
            lower_inner_lip_y = landmarks[14].y
            lower_outer_lip_y = landmarks[17].y
            total_lip_height = abs(lower_outer_lip_y - upper_outer_lip_y)
            inner_lip_height = lower_inner_lip_y - upper_inner_lip_y

            current_lip_bite = False
            if total_lip_height > 0.005:
                avg_lip_ratio = inner_lip_height / total_lip_height

                if avg_lip_ratio < self.LIP_BITE_RATIO_THRESHOLD:
                    current_lip_bite = True

            if current_lip_bite:
                self.lip_bite_status = True
                self.lip_bite_counter += 1

                if self.lip_bite_counter >= self.LIP_BITE_FRAME_COUNT and not self.last_lip_bite_event:
                    self.last_lip_bite_event = True
                    self.total_lip_bite_events += 1
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    self.timeline['입술깨물기'].append(time_str)
                    events.append("입술깨물기")
            else:
                if self.last_lip_bite_event:
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    events.append("입술깨물기 해제")
                self.lip_bite_status = False
                self.lip_bite_counter = 0
                self.last_lip_bite_event = False

            # === 3. Blink ===
            left_v_top = landmarks[386]
            left_v_bottom = landmarks[374]
            left_h_left = landmarks[362]
            left_h_right = landmarks[263]
            right_v_top = landmarks[159]
            right_v_bottom = landmarks[145]
            right_h_left = landmarks[133]
            right_h_right = landmarks[33]

            left_vertical_dist = self.get_distance_2d(left_v_top, left_v_bottom, width, height)
            left_horizontal_dist = self.get_distance_2d(left_h_left, left_h_right, width, height)
            right_vertical_dist = self.get_distance_2d(right_v_top, right_v_bottom, width, height)
            right_horizontal_dist = self.get_distance_2d(right_h_left, right_h_right, width, height)

            current_blink = False
            if left_horizontal_dist > 0 and right_horizontal_dist > 0:
                left_ear = left_vertical_dist / left_horizontal_dist
                right_ear = right_vertical_dist / right_horizontal_dist
                avg_ear = (left_ear + right_ear) / 2.0

                if avg_ear < self.EAR_THRESHOLD:
                    current_blink = True

            if current_blink:
                self.blink_counter += 1
                self.blink_status_closed = True
            else:
                if self.blink_counter >= self.BLINK_FRAME_COUNT:
                    current_time = time.time()
                    self.blink_timestamps.append(current_time)
                    self.total_blink_events += 1

                    recent_blinks = [t for t in self.blink_timestamps
                                     if current_time - t <= 10.0]
                    blink_count = len(recent_blinks)

                    if blink_count >= self.FREQUENT_BLINK_THRESHOLD:
                        if not self.last_frequent_blink_event:
                            self.last_frequent_blink_event = True
                            #elapsed = time.time() - self.start_time
                            #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                            self.timeline['눈깜빡임'].append(time_str)
                            events.append(f"잦은 눈깜빡임 ({blink_count}회/10초)")
                    else:
                        if self.last_frequent_blink_event:
                            #elapsed = time.time() - self.start_time
                            #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                            events.append("잦은 눈깜빡임 해제")
                        self.last_frequent_blink_event = False

                self.blink_counter = 0
                self.blink_status_closed = False

            # === 4. Head Shake ===
            left_ear = landmarks[234]
            right_ear = landmarks[454]

            self.left_ear_positions.append(left_ear.x)
            self.right_ear_positions.append(right_ear.x)

            if len(self.left_ear_positions) >= self.HEAD_SHAKE_FRAME_COUNT:
                left_range = max(self.left_ear_positions) - min(self.left_ear_positions)
                right_range = max(self.right_ear_positions) - min(self.right_ear_positions)

                if left_range > self.HEAD_SHAKE_THRESHOLD or right_range > self.HEAD_SHAKE_THRESHOLD:
                    if not self.last_head_shake_event:
                        self.last_head_shake_event = True
                        self.total_head_shake_events += 1
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        self.timeline['고개흔들기'].append(time_str)
                        events.append("고개흔들기")

                    self.left_ear_positions.clear()
                    self.right_ear_positions.clear()
                else:
                    if self.last_head_shake_event:
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        events.append("고개흔들기 해제")
                    self.last_head_shake_event = False

        except Exception as e:
            pass

        return events

    def _process_pose(self, pose_landmarks, width, height, timestamp_ms):
        """포즈 분석"""
        events = []
        landmarks = pose_landmarks.landmark
        time_str = self._format_time(timestamp_ms)

        try:
            # === 1. Slant & Rigid ===
            left_shoulder = landmarks[self.mp_holistic.PoseLandmark.LEFT_SHOULDER.value]
            right_shoulder = landmarks[self.mp_holistic.PoseLandmark.RIGHT_SHOULDER.value]

            if (left_shoulder.visibility > self.VISIBILITY_THRESHOLD and
                    right_shoulder.visibility > self.VISIBILITY_THRESHOLD):

                shoulder_width_px = self.get_distance_2d(left_shoulder, right_shoulder, width, height)
                shoulder_y_diff_px = abs(int(left_shoulder.y * height) - int(right_shoulder.y * height))

                # Slant
                slant_ratio = 0.0
                if shoulder_width_px > 0:
                    slant_ratio = shoulder_y_diff_px / shoulder_width_px

                current_slant_pose = slant_ratio > self.SLANT_RATIO_THRESHOLD

                if current_slant_pose:
                    self.slant_status = True
                    self.slant_counter += 1

                    if self.slant_counter >= self.SLANT_FRAME_COUNT and not self.last_slant_event:
                        self.last_slant_event = True
                        self.total_slant_events += 1
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        self.timeline['비스듬한자세'].append(time_str)
                        events.append("비스듬한자세")
                else:
                    if self.last_slant_event:
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        events.append("비스듬한자세 해제")
                    self.slant_status = False
                    self.slant_counter = 0
                    self.last_slant_event = False

                # Rigid
                if shoulder_width_px > 10:
                    left_wrist = landmarks[self.mp_holistic.PoseLandmark.LEFT_WRIST.value]
                    right_wrist = landmarks[self.mp_holistic.PoseLandmark.RIGHT_WRIST.value]
                    left_hip = landmarks[self.mp_holistic.PoseLandmark.LEFT_HIP.value]
                    right_hip = landmarks[self.mp_holistic.PoseLandmark.RIGHT_HIP.value]

                    if (left_wrist.visibility > self.VISIBILITY_THRESHOLD and
                            right_wrist.visibility > self.VISIBILITY_THRESHOLD and
                            left_hip.visibility > self.VISIBILITY_THRESHOLD and
                            right_hip.visibility > self.VISIBILITY_THRESHOLD):

                        dynamic_attention_thresh = shoulder_width_px * self.ATTENTION_DIST_RATIO
                        dynamic_rigid_thresh = shoulder_width_px * self.RIGID_MOVEMENT_RATIO

                        dist_l_wrist_hip = self.get_distance_2d(left_wrist, left_hip, width, height)
                        dist_r_wrist_hip = self.get_distance_2d(right_wrist, right_hip, width, height)

                        is_attention_pose = (dist_l_wrist_hip < dynamic_attention_thresh and
                                             dist_r_wrist_hip < dynamic_attention_thresh)

                        lw_x_px, lw_y_px = int(left_wrist.x * width), int(left_wrist.y * height)
                        rw_x_px, rw_y_px = int(right_wrist.x * width), int(right_wrist.y * height)
                        self.left_wrist_history.append((lw_x_px, lw_y_px))
                        self.right_wrist_history.append((rw_x_px, rw_y_px))

                        is_rigid = False
                        if len(self.left_wrist_history) == self.RIGID_FRAME_COUNT:
                            l_std_x = np.std([pos[0] for pos in self.left_wrist_history])
                            l_std_y = np.std([pos[1] for pos in self.left_wrist_history])
                            r_std_x = np.std([pos[0] for pos in self.right_wrist_history])
                            r_std_y = np.std([pos[1] for pos in self.right_wrist_history])
                            movement_std = (l_std_x + l_std_y + r_std_x + r_std_y) / 4.0

                            if movement_std < dynamic_rigid_thresh:
                                is_rigid = True

                        current_rigid_pose = is_attention_pose and is_rigid

                        if current_rigid_pose:
                            self.rigid_status = True
                            self.rigid_counter += 1

                            if not self.last_rigid_event:
                                self.last_rigid_event = True
                                self.total_rigid_events += 1
                                #elapsed = time.time() - self.start_time
                                #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                                self.timeline['경직된차려'].append(time_str)
                                events.append("경직된차려")
                        else:
                            if self.last_rigid_event:
                                #elapsed = time.time() - self.start_time
                                #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                                events.append("경직된차려 해제")
                            self.rigid_status = False
                            self.rigid_counter = 0
                            self.last_rigid_event = False

            # === 2. Arms Crossed ===
            left_wrist = landmarks[self.mp_holistic.PoseLandmark.LEFT_WRIST.value]
            right_wrist = landmarks[self.mp_holistic.PoseLandmark.RIGHT_WRIST.value]
            left_elbow = landmarks[self.mp_holistic.PoseLandmark.LEFT_ELBOW.value]
            right_elbow = landmarks[self.mp_holistic.PoseLandmark.RIGHT_ELBOW.value]
            left_shoulder = landmarks[self.mp_holistic.PoseLandmark.LEFT_SHOULDER.value]
            right_shoulder = landmarks[self.mp_holistic.PoseLandmark.RIGHT_SHOULDER.value]

            current_arms_crossed = False

            left_visible = left_elbow.visibility > self.ARMS_CROSSED_ELBOW_VISIBILITY
            right_visible = right_elbow.visibility > self.ARMS_CROSSED_ELBOW_VISIBILITY

            if left_visible or right_visible:
                body_center_x = (left_shoulder.x + right_shoulder.x) / 2

                left_arm_crossed = False
                right_arm_crossed = False

                if left_visible:
                    left_elbow_crossed = left_elbow.x > body_center_x
                    left_range_min = body_center_x
                    left_range_max = left_shoulder.x + 0.15
                    left_elbow_inside = left_range_min < left_elbow.x < left_range_max

                    left_wrist_ok = True
                    if left_wrist.visibility > 0.5:
                        left_wrist_ok = left_wrist.x < body_center_x

                    left_arm_crossed = left_elbow_crossed and left_elbow_inside and left_wrist_ok

                if right_visible:
                    right_elbow_crossed = right_elbow.x < body_center_x
                    right_range_min = right_shoulder.x - 0.15
                    right_range_max = body_center_x
                    right_elbow_inside = right_range_min < right_elbow.x < right_range_max

                    right_wrist_ok = True
                    if right_wrist.visibility > 0.5:
                        right_wrist_ok = right_wrist.x > body_center_x

                    right_arm_crossed = right_elbow_crossed and right_elbow_inside and right_wrist_ok

                current_arms_crossed = left_arm_crossed or right_arm_crossed

            if current_arms_crossed:
                self.arms_crossed_counter += 1
                if (self.arms_crossed_counter >= self.ARMS_CROSSED_FRAME_COUNT and
                        not self.last_arms_crossed_event):
                    self.last_arms_crossed_event = True
                    self.total_arms_crossed_events += 1
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    self.timeline['팔짱끼기'].append(time_str)
                    events.append("팔짱끼기")
            else:
                if self.last_arms_crossed_event:
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    events.append("팔짱끼기 해제")
                self.arms_crossed_counter = 0
                self.last_arms_crossed_event = False

            # === 3. Hands Behind Back ===
            left_wrist = landmarks[self.mp_holistic.PoseLandmark.LEFT_WRIST.value]
            right_wrist = landmarks[self.mp_holistic.PoseLandmark.RIGHT_WRIST.value]
            left_elbow = landmarks[self.mp_holistic.PoseLandmark.LEFT_ELBOW.value]
            right_elbow = landmarks[self.mp_holistic.PoseLandmark.RIGHT_ELBOW.value]
            left_shoulder = landmarks[self.mp_holistic.PoseLandmark.LEFT_SHOULDER.value]
            right_shoulder = landmarks[self.mp_holistic.PoseLandmark.RIGHT_SHOULDER.value]

            current_hands_behind = False

            left_hand_behind = False
            right_hand_behind = False

            left_wrist_hidden = left_wrist.visibility < 0.4
            left_elbow_behind = left_elbow.z > left_shoulder.z + 0.05
            left_hand_behind = left_wrist_hidden and left_elbow_behind

            right_wrist_hidden = right_wrist.visibility < 0.4
            right_elbow_behind = right_elbow.z > right_shoulder.z + 0.05
            right_hand_behind = right_wrist_hidden and right_elbow_behind

            current_hands_behind = left_hand_behind or right_hand_behind

            if current_hands_behind:
                self.hands_behind_back_counter += 1
                if (self.hands_behind_back_counter >= self.HANDS_BEHIND_BACK_FRAME_COUNT and
                        not self.last_hands_behind_back_event):
                    self.last_hands_behind_back_event = True
                    self.total_hands_behind_back_events += 1
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    self.timeline['뒷짐'].append(time_str)
                    events.append("뒷짐")
            else:
                if self.last_hands_behind_back_event:
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    events.append("뒷짐 해제")
                self.hands_behind_back_counter = 0
                self.last_hands_behind_back_event = False

            # === 4. Fig Leaf ===
            left_wrist = landmarks[self.mp_holistic.PoseLandmark.LEFT_WRIST.value]
            right_wrist = landmarks[self.mp_holistic.PoseLandmark.RIGHT_WRIST.value]
            left_hip = landmarks[self.mp_holistic.PoseLandmark.LEFT_HIP.value]
            right_hip = landmarks[self.mp_holistic.PoseLandmark.RIGHT_HIP.value]

            if (left_wrist.visibility > 0.5 and right_wrist.visibility > 0.5 and
                    left_hip.visibility > 0.5 and right_hip.visibility > 0.5):

                dist_hands = self.get_distance_2d(left_wrist, right_wrist, width, height)
                hip_center_y = (left_hip.y + right_hip.y) / 2.0
                hands_center_y = (left_wrist.y + right_wrist.y) / 2.0

                current_fig_leaf_pose = False
                if dist_hands < self.FIG_LEAF_HANDS_CLOSE_THRESHOLD:
                    y_diff = hands_center_y - hip_center_y
                    if -0.1 < y_diff:
                        current_fig_leaf_pose = True

                if current_fig_leaf_pose:
                    self.fig_leaf_status = True
                    self.fig_leaf_counter += 1

                    if self.fig_leaf_counter >= self.FIG_LEAF_FRAME_COUNT and not self.last_fig_leaf_event:
                        self.last_fig_leaf_event = True
                        self.total_fig_leaf_events += 1
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        self.timeline['무화과잎자세'].append(time_str)
                        events.append("무화과잎자세")
                else:
                    if self.last_fig_leaf_event:
                        #elapsed = time.time() - self.start_time
                        #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                        events.append("무화과잎자세 해제")
                    self.fig_leaf_status = False
                    self.fig_leaf_counter = 0
                    self.last_fig_leaf_event = False

        except Exception as e:
            pass

        return events

    def _process_hands(self, results_holistic, face_landmarks, width, height, timestamp_ms):
        """손 분석"""
        events = []
        time_str = self._format_time(timestamp_ms)

        try:
            # === 1. Hand to Face Region ===
            face_region = self._detect_hand_to_face_region(
                results_holistic.pose_landmarks,
                results_holistic.left_hand_landmarks,
                results_holistic.right_hand_landmarks,
                face_landmarks,
                width, height
            )

            if face_region:
                if face_region != self.last_hand_to_face_event:
                    self.last_hand_to_face_event = face_region

                    if face_region not in self.total_hand_to_face_events:
                        self.total_hand_to_face_events[face_region] = 0

                    self.total_hand_to_face_events[face_region] += 1
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    self.timeline[face_region].append(time_str)
                    events.append(face_region)
            else:
                self.last_hand_to_face_event = None

            # === 2. Hand Rubbing ===
            if self._detect_hand_rubbing(
                    results_holistic.left_hand_landmarks,
                    results_holistic.right_hand_landmarks):
                if not self.last_hand_rubbing_event:
                    self.last_hand_rubbing_event = True
                    self.total_hand_rubbing_events += 1
                    #elapsed = time.time() - self.start_time
                    #time_str = time.strftime('%H:%M:%S', time.gmtime(elapsed))
                    self.timeline['손비비기'].append(time_str)
                    events.append("손비비기")
            else:
                self.last_hand_rubbing_event = False

        except Exception as e:
            pass

        return events

    def _detect_hand_to_face_region(self, pose_landmarks, left_hand_landmarks,
                                    right_hand_landmarks, face_landmarks, width, height):
        """손-얼굴 접촉 부위 감지"""
        if not pose_landmarks or not face_landmarks:
            return None

        nose_tip = face_landmarks.landmark[1]
        forehead = face_landmarks.landmark[10]
        chin = face_landmarks.landmark[152]
        left_ear = face_landmarks.landmark[234]
        right_ear = face_landmarks.landmark[454]
        upper_lip = face_landmarks.landmark[13]
        lower_lip = face_landmarks.landmark[14]

        mouth_center_x = (upper_lip.x + lower_lip.x) / 2
        mouth_center_y = (upper_lip.y + lower_lip.y) / 2

        face_center_x = nose_tip.x
        face_center_y = nose_tip.y
        face_width = abs(right_ear.x - left_ear.x)
        face_height_top = abs(nose_tip.y - forehead.y)
        face_height_bottom = abs(chin.y - nose_tip.y)

        head_center = (nose_tip.x, forehead.y - face_height_top * 0.5)
        forehead_center = (nose_tip.x, forehead.y + face_height_top * 0.1)
        nose_center = (nose_tip.x, nose_tip.y)
        mouth_center = (mouth_center_x, mouth_center_y)
        chin_center = (nose_tip.x, chin.y)
        left_ear_center = (left_ear.x, left_ear.y)
        right_ear_center = (right_ear.x, right_ear.y)

        def check_hand_region(hand_landmarks):
            if not hand_landmarks:
                return None

            fingertips = [4, 8, 12, 16, 20]
            min_distance_overall = float('inf')
            closest_region = None

            for idx in fingertips:
                point = hand_landmarks.landmark[idx]
                distance_x = abs(point.x - face_center_x)

                if distance_x > face_width * 1.5 or point.y > chin.y + face_height_bottom * 0.5:
                    continue

                distances = {}

                if point.y < forehead.y:
                    distances['머리터치'] = math.sqrt(
                        (point.x - head_center[0]) ** 2 + (point.y - head_center[1]) ** 2
                    )

                if forehead.y - face_height_top * 0.3 < point.y < nose_tip.y:
                    distances['이마터치'] = math.sqrt(
                        (point.x - forehead_center[0]) ** 2 + (point.y - forehead_center[1]) ** 2
                    )

                if nose_tip.y - face_height_top * 0.3 < point.y < nose_tip.y + face_height_bottom * 0.3:
                    distances['코터치'] = math.sqrt(
                        (point.x - nose_center[0]) ** 2 + (point.y - nose_center[1]) ** 2
                    )

                if nose_tip.y + face_height_bottom * 0.1 < point.y < chin.y - face_height_bottom * 0.2:
                    distances['입술터치'] = math.sqrt(
                        (point.x - mouth_center[0]) ** 2 + (point.y - mouth_center[1]) ** 2
                    )

                if point.y > nose_tip.y + face_height_bottom * 0.2:
                    distances['턱터치'] = math.sqrt(
                        (point.x - chin_center[0]) ** 2 + (point.y - chin_center[1]) ** 2
                    )

                left_ear_dist = math.sqrt(
                    (point.x - left_ear_center[0]) ** 2 + (point.y - left_ear_center[1]) ** 2
                )
                if left_ear_dist < 0.20:
                    distances['왼쪽귀터치'] = left_ear_dist

                right_ear_dist = math.sqrt(
                    (point.x - right_ear_center[0]) ** 2 + (point.y - right_ear_center[1]) ** 2
                )
                if right_ear_dist < 0.20:
                    distances['오른쪽귀터치'] = right_ear_dist

                if distances:
                    min_region = min(distances, key=distances.get)
                    min_distance = distances[min_region]

                    if min_distance < 0.20:
                        if min_distance < min_distance_overall:
                            min_distance_overall = min_distance
                            closest_region = min_region

            return closest_region

        detected_region = check_hand_region(left_hand_landmarks)
        if not detected_region:
            detected_region = check_hand_region(right_hand_landmarks)

        if detected_region:
            self.hand_face_distances.append(detected_region)
        else:
            self.hand_face_distances.append(None)

        if len(self.hand_face_distances) >= 8:
            recent_detections = list(self.hand_face_distances)[-8:]
            valid_regions = [r for r in recent_detections if r is not None]

            if len(valid_regions) >= 5:
                from collections import Counter
                most_common = Counter(valid_regions).most_common(1)[0][0]
                self.hand_face_distances.clear()
                return most_common

        return None

    def _detect_hand_rubbing(self, left_hand_landmarks, right_hand_landmarks):
        """손 비비기 감지"""
        if not left_hand_landmarks or not right_hand_landmarks:
            return False

        if (self.last_arms_crossed_event or
                self.last_hands_behind_back_event or
                self.last_hand_to_face_event is not None):
            return False

        def get_palm_center(hand_landmarks):
            palm_points = [0, 5, 9, 13, 17]
            x = sum(hand_landmarks.landmark[i].x for i in palm_points) / len(palm_points)
            y = sum(hand_landmarks.landmark[i].y for i in palm_points) / len(palm_points)
            return (x, y)

        left_palm = get_palm_center(left_hand_landmarks)
        right_palm = get_palm_center(right_hand_landmarks)

        distance = math.sqrt((left_palm[0] - right_palm[0]) ** 2 +
                             (left_palm[1] - right_palm[1]) ** 2)

        self.rubbing_history.append({
            'left': left_palm,
            'right': right_palm,
            'distance': distance
        })

        if len(self.rubbing_history) < self.HAND_RUBBING_FRAME_COUNT:
            return False

        recent_distances = [f['distance'] for f in list(self.rubbing_history)[-20:]]
        avg_distance = sum(recent_distances) / len(recent_distances)

        if avg_distance > self.HAND_RUBBING_DISTANCE:
            return False

        opposite_with_movement = 0
        total_movement = 0
        history_list = list(self.rubbing_history)

        for i in range(1, len(history_list)):
            prev = history_list[i - 1]
            curr = history_list[i]

            left_dx = curr['left'][0] - prev['left'][0]
            right_dx = curr['right'][0] - prev['right'][0]
            left_dy = curr['left'][1] - prev['left'][1]
            right_dy = curr['right'][1] - prev['right'][1]

            combined_movement = math.sqrt(left_dx ** 2 + left_dy ** 2 +
                                          right_dx ** 2 + right_dy ** 2)

            if combined_movement > self.HAND_RUBBING_MOVEMENT:
                total_movement += 1

                if (left_dx * right_dx < 0) or (left_dy * right_dy < 0):
                    opposite_with_movement += 1

        return total_movement >= 15 and opposite_with_movement >= 10

    def get_statistics(self):
        """통계 정보 반환"""
        stats = {
            '고개숙이기': self.total_head_bow_events,
            '천장보기': self.total_ceiling_events,
            '입술깨물기': self.total_lip_bite_events,
            '눈깜빡임': self.total_blink_events,
            '고개흔들기': self.total_head_shake_events,
            '비스듬한자세': self.total_slant_events,
            '경직된차려': self.total_rigid_events,
            '팔짱끼기': self.total_arms_crossed_events,
            '뒷짐': self.total_hands_behind_back_events,
            '손비비기': self.total_hand_rubbing_events,
            '무화과잎자세': self.total_fig_leaf_events
        }

        for region, count in self.total_hand_to_face_events.items():
            stats[region] = count

        return stats

    def close(self):
        """리소스 정리"""
        self.holistic.close()
        self.face_mesh.close()