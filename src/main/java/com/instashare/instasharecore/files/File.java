package com.instashare.instasharecore.files;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(value = "files")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class File {
  private @Id String id;

  @Indexed(unique = true)
  private String fileName;

  private String owner;
  private FileStatus fileStatus;
  private Long size;
  private String mimeType;
}
