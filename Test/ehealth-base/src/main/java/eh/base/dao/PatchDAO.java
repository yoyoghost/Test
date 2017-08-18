package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.Patch;

import java.util.List;

public abstract class PatchDAO extends HibernateSupportDelegateDAO<Patch> {

    public PatchDAO(){
        super();
        this.setKeyField("id");
    }

    @DAOMethod(sql = "from Patch where fileName=:fileName and isPatch is null order by fileVersion desc", limit = 2)
    public abstract List<Patch> findByFileNameAndIsPatchIsNull(@DAOParam("fileName") String fileName);

    @DAOMethod(sql = "from Patch where fileName=:fileName and fileVersion=:fileVersion and isPatch=1")
    public abstract Patch getByFileNameAndFileVersionAndIsPatchIs1(@DAOParam("fileName") String fileName, @DAOParam("fileVersion") String fileVersion);
}
