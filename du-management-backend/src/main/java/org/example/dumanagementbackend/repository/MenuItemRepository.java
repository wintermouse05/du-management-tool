package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
}
