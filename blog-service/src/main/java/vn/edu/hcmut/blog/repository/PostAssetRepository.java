package vn.edu.hcmut.blog.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmut.blog.entity.PostAsset;

public interface PostAssetRepository extends JpaRepository<PostAsset, String> {

    void deleteByResourceId(String resourceId);

    /**
     * Returns all assets for one post.
     */
    List<PostAsset> findByResourceId(String resourceId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PostAsset a WHERE a.resourceId IN :resourceIds")
    void deleteByResourceIdIn(@Param("resourceIds") List<String> resourceIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM PostAsset pa WHERE pa.resourceId = :resourceId AND pa.id IN :ids")
    void deleteByResourceIdAndIdIn(@Param("resourceId") String resourceId,
                                   @Param("ids") Collection<String> ids);
}
