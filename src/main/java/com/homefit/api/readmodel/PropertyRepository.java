package com.homefit.api.readmodel;

import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

/** Read-only — bare {@link Repository} exposes no save/delete surface. */
public interface PropertyRepository extends Repository<Property, UUID> {
    Optional<Property> findById(UUID id);
}
