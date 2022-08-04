package com.instashare.instasharecore.files.dtos;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UploadResultError extends UploadResultContent {
  private final String message;
}
