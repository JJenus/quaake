package com.homefit.api.readmodel;

import org.springframework.data.repository.Repository;
import java.util.List;

public interface CellProximityRepository extends Repository<CellProximity, CellProximityId> {
    List<CellProximity> findById_CellH3(Long cellH3);
}
