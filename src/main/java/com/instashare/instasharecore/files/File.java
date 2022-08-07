package com.instashare.instasharecore.files;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(value = "files")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@CompoundIndex(name = "owner_fileName_idx", def = "{'owner' : 1, 'fileName' : 1}", unique = true)
public class File {
  private @Id String id;

  @Setter private String fileName;

  private String owner;
  @Setter private FileStatus fileStatus;
  private Long size;
  private String mimeType;
}
