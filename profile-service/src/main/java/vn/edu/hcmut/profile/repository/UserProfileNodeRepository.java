package vn.edu.hcmut.profile.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;

@Repository
public interface UserProfileNodeRepository extends Neo4jRepository<UserProfileNode, String> {}
