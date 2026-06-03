package com.homefit.api.readmodel;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Read-only — extends bare {@link Repository} so no save/delete surface is inherited. */
public interface PropertyRepository extends Repository<Property, UUID> {

    Optional<Property> findById(UUID id);
}
