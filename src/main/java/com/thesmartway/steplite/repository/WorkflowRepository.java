package com.thesmartway.steplite.repository;

import com.thesmartway.steplite.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    
    Optional<Workflow> findByName(String name);
    
    List<Workflow> findByNameContainingIgnoreCase(String name);
    
    @Query("SELECT w FROM Workflow w LEFT JOIN FETCH w.versions WHERE w.id = :id")
    Optional<Workflow> findByIdWithVersions(@Param("id") Long id);
    
    @Query("SELECT w FROM Workflow w LEFT JOIN FETCH w.versions")
    List<Workflow> findAllWithVersions();
}
