package com.example.speechmate_backend.user.repository;

import com.example.speechmate_backend.user.domain.OauthInfo;
import com.example.speechmate_backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {


    Optional<User> findByOauthInfo(OauthInfo oauthInfo);
}
