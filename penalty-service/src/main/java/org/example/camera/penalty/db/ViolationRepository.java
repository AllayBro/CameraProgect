package org.example.camera.penalty.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ViolationRepository extends JpaRepository<ViolationEntity, String> {
}
