package com.app.to_do.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.app.to_do.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    
}
