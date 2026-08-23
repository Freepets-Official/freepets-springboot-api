package com.freepets.domain.facility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "check_lists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_list_id")
    private Long checkListId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Requirement type;

    @Column(name = "is_checked", nullable = false)
    private boolean isChecked;

    @Builder
    private CheckList(
            Facility facility,
            Requirement type,
            boolean isChecked
    ) {
        this.facility = facility;
        this.type = type;
        this.isChecked = isChecked;
    }

}
