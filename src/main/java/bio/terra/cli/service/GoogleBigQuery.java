package bio.terra.cli.service;

import com.google.api.client.http.HttpStatusCodes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility methods for calling Google Cloud BigQuery endpoints. */
public class GoogleBigQuery {
  private static final Logger logger = LoggerFactory.getLogger(GoogleCloudStorage.class);

  private final BigQuery bigQuery;

  public GoogleBigQuery(GoogleCredentials credentials, String projectId) {
    bigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .build()
            .getService();
  }

  /** Returns whether we have permission to list tables in the dataset. */
  public boolean checkListTablesAccess(DatasetId datasetId) {
    try {
      bigQuery.listTables(datasetId);
      return true;
    } catch (BigQueryException e) {
      logger.debug("BigQuery exception = {}", e);
      if (e.getCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND
          || e.getCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
        return false;
      }
      throw e;
    }
  }
}