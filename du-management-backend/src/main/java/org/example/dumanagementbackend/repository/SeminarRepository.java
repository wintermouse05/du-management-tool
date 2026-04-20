package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.Seminar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeminarRepository extends JpaRepository<Seminar, Long> {
}
