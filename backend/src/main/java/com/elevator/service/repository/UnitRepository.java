package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.Unit;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findAllByOrderByBuildingIdAscUnitNoAsc();

    Optional<Unit> findByUnitNo(String unitNo);

    boolean existsByUnitNo(String unitNo);
}
