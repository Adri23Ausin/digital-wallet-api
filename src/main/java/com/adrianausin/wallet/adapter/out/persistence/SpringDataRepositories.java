package com.adrianausin.wallet.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);
}

interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {

    List<WalletEntity> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);
}

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {
}

interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    Page<LedgerEntryEntity> findByWalletIdOrderBySeqDesc(UUID walletId, Pageable pageable);
}
