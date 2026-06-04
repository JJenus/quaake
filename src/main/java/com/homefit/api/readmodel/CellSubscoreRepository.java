package com.homefit.api.readmodel;

import org.springframework.data.repository.Repository;
import java.util.List;

/** Read-only — only the lookups the API needs. */
public interface CellSubscoreRepository extends Repository<CellSubscore, CellSubscoreId> {
    List<CellSubscore> findById_CellH3(Long cellH3);
}
