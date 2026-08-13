package com.freepets.domain.report.entity;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

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
@Table(name = "facility_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_reason", nullable = false)
    private DenialReason denialReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "is_realtime", nullable = false)
    private boolean isRealtime;

    @Column(name = "photo_proof", columnDefinition = "TEXT")
    private String photoProof;

    @Builder
    private FacilityReport(
            User user,
            Facility facility,
            String content,
            ReportType reportType,
            DenialReason denialReason,
            ReportStatus status,
            boolean isRealtime,
            String photoProof
    ) {
        this.user = user;
        this.facility = facility;
        this.content = content;
        this.reportType = reportType;
        this.denialReason = denialReason;
        this.status = status;
        this.isRealtime = isRealtime;
        this.photoProof = photoProof;
    }

}
