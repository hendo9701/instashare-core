package com.instashare.instasharecore.files.dtos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UploadResultError extends UploadResultContent {
  private final String message;
}
