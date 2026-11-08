package com.curiosity.agent_backend.repository;

import com.curiosity.agent_backend.domain.ScriptPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ScriptPartRepository extends JpaRepository<ScriptPart, Long> {

    List<ScriptPart> findByProjectIdOrderByOrderIndexAsc(Long projectId);

    // Transaction dışında (uzun TTS/görsel çağrıları) project alanına
    // erişilecekse lazy proxy patlamasın diye join fetch ile çekilir
    @Query("select p from ScriptPart p join fetch p.project where p.id = :id")
    Optional<ScriptPart> findWithProjectById(Long id);
}
