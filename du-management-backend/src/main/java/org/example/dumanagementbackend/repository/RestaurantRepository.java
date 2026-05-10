package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
}
