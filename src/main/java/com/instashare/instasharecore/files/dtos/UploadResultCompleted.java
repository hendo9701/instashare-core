package com.instashare.instasharecore.files.dtos;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class UploadResultCompleted extends UploadResultContent {
  private final List<String> keys;
}
