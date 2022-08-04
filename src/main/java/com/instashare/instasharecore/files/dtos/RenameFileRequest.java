package com.instashare.instasharecore.files.dtos;

import javax.validation.constraints.NotBlank;

public record RenameFileRequest(@NotBlank String newName) {}
