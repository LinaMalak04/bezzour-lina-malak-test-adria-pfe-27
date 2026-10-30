package com.adria.bank.account.repository;

import com.adria.bank.account.domain.AppliedOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppliedOperationRepository extends JpaRepository<AppliedOperation, UUID> {
}
