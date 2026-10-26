package sk.testlab.cvicenie05.db;

import java.util.List;

public record ReadOnlyCheck(
        int grantRows,
        boolean selectPrivilegeSeen,
        List<String> forbiddenPrivileges) {

    public boolean passed() {
        return selectPrivilegeSeen && forbiddenPrivileges.isEmpty();
    }
}
