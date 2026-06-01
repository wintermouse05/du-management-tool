package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.MenuItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndDeletedAtIsNull(Long restaurantId);

    Optional<MenuItem> findByRestaurantIdAndName(Long restaurantId, String name);

    void deleteByRestaurantId(Long restaurantId);
}
