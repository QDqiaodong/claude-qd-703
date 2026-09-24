package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.PartReplacement;

public interface PartReplacementRepository extends JpaRepository<PartReplacement, Long> {

    List<PartReplacement> findAllByOrderByReplaceDateDescIdDesc();
}
