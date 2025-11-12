package com.cromoteca.bfts.mcp;

/** Abstraction for querying backed up file metadata. */
public interface FilesViewQueryService {

  FilesViewQueryResult execute(FilesViewQueryRequest request);
}
