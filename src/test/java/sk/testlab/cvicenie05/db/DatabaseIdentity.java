package sk.testlab.cvicenie05.db;

public record DatabaseIdentity(
        String databaseName,
        String currentUser,
        String serverVersion) {
}
