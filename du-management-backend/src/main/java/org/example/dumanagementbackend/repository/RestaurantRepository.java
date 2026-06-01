package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.Restaurant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByDeletedAtIsNull();

    Optional<Restaurant> findByIdAndDeletedAtIsNull(Long id);
}
