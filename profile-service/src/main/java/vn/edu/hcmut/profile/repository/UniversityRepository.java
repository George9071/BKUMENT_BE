package vn.edu.hcmut.profile.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.profile.entity.jpa.University;

public interface UniversityRepository extends JpaRepository<University, Integer> {
    List<University> findByNameContainingIgnoreCaseOrAbbreviationContainingIgnoreCase(String name, String abbreviation);
}
