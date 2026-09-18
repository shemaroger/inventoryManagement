package com.company.tai.common.service;

import com.company.tai.common.dto.AuditLogDto;
import com.company.tai.common.entity.AuditLog;
import com.company.tai.common.repository.AuditLogRepository;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public Page<AuditLogDto> search(Long actorId, String action, String targetType,
                                     Instant from, Instant to, String search, Pageable pageable) {
        // A free-text search term matches either the recorded detail (method/path/status) or
        // the name/email of whichever user performed the action — the two things a reader is
        // actually likely to type in.
        List<Long> matchingActorIds = (search != null && !search.isBlank())
                ? userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search)
                        .stream().map(User::getId).toList()
                : List.of();

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorId != null) predicates.add(cb.equal(root.get("actorId"), actorId));
            if (action != null && !action.isBlank()) predicates.add(cb.equal(root.get("action"), action.toUpperCase()));
            if (targetType != null && !targetType.isBlank()) predicates.add(cb.equal(root.get("targetType"), targetType));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));

            if (search != null && !search.isBlank()) {
                String term = "%" + search.toLowerCase() + "%";
                Predicate detailMatch = cb.like(cb.lower(root.get("detail")), term);
                Predicate actorMatch = matchingActorIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("actorId").in(matchingActorIds);
                predicates.add(cb.or(detailMatch, actorMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);

        Map<Long, String> actorNames = userRepository
                .findAllById(page.getContent().stream()
                        .map(AuditLog::getActorId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return page.map(entry -> new AuditLogDto(
                entry.getId(),
                entry.getActorId(),
                entry.getActorId() != null ? actorNames.getOrDefault(entry.getActorId(), "Deleted user") : "System",
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDetail(),
                entry.getIpAddress(),
                entry.getCreatedAt()
        ));
    }

    public List<String> listTargetTypes() {
        return auditLogRepository.findAll().stream()
                .map(AuditLog::getTargetType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> listActions() {
        return auditLogRepository.findAll().stream()
                .map(AuditLog::getAction)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
