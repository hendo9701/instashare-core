package com.instashare.instasharecore.files;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileRepository extends ReactiveMongoRepository<File, String> {

  Mono<File> save(File file);

  Mono<Boolean> existsByFileName(String fileName);

  Flux<File> findAllByOwner(String owner);

  Mono<File> findByIdAndOwner(String fileKey, String owner);

  @Query(value = "{ 'owner': ?0}", count = true)
  Mono<Long> countByOwner(String name);
}
