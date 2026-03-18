package vn.edu.hcmut.profile.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmut.profile.entity.jpa.University;

public interface UniversityRepository extends JpaRepository<University, Integer> {
    List<University> findByNameContainingIgnoreCaseOrAbbreviationContainingIgnoreCase(String name, String abbreviation);

    @Query(
            "SELECT u FROM University u WHERE "
                    + "LOWER(FUNCTION('unaccent', u.name)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :keyword, '%'))) OR "
                    + "LOWER(FUNCTION('unaccent', u.abbreviation)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :keyword, '%')))")
    Page<University> search(@Param("keyword") String keyword, Pageable pageable);
}
