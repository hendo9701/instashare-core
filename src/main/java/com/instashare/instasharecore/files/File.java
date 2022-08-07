package com.instashare.instasharecore.files;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(value = "files")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class File {
  private @Id String id;

  @Indexed(unique = true)
  @Setter
  private String fileName;

  private String owner;
  @Setter private FileStatus fileStatus;
  private Long size;
  private String mimeType;
}
