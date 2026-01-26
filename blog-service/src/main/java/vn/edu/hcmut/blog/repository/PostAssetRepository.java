package vn.edu.hcmut.blog.repository;

import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import vn.edu.hcmut.blog.entity.PostAsset;

public interface PostAssetRepository extends JpaRepository<PostAsset, String> {
    List<PostAsset> findByResourceId(String resourceId);

    @Transactional
    @Modifying
    void deleteByResourceId(String resourceId);
}
