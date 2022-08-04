package com.instashare.instasharecore.files.dtos;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Data
public class UploadResultCompleted extends UploadResultContent {
  private final List<String> keys;
}
