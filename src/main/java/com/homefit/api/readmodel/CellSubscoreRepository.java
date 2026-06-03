package com.homefit.api.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CellSubscoreRepository extends JpaRepository<CellSubscore, CellSubscoreId> {

    List<CellSubscore> findById_CellH3(Long cellH3);
}
