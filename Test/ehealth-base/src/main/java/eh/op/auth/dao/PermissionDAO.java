package eh.op.auth.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.opauth.Permission;

import java.util.List;

public abstract class PermissionDAO extends HibernateSupportDelegateDAO<Permission> {
    public PermissionDAO() {
        super();
        this.setEntityName(Permission.class.getName());
        this.setKeyField("pid");
    }

    @DAOMethod(sql = "from Permission p left outer join fetch p.acls")
    public abstract List<Permission> findAll();
}
