package vn.edu.hcmut.lms.repository.projection;

public interface TutorRatingAggregate {
    Double getWeightedRatingSum();

    Long getRatingCount();
}
