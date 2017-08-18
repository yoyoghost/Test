package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.cdr.bean.DepDetailBean;
import eh.cdr.dao.DrugsEnterpriseDAO;
import eh.cdr.drugsenterprise.RemoteDrugEnterpriseService;
import eh.entity.cdr.DrugsEnterprise;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/7/3.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class RecipePatientServiceTest extends AbstractJUnit4SpringContextTests {

    @Test
    public void findRecipesForDoctor() throws Exception {
        RecipePatientService recipePatientService = AppContextHolder.getBean("eh.recipePatientService", RecipePatientService.class);
        show(recipePatientService.findSupportDepList(1, Arrays.asList(2218)));
    }

    @Test
    public void findSupportDep(){
        RemoteDrugEnterpriseService remoteDrugService = AppContextHolder.getBean("eh.remoteDrugService", RemoteDrugEnterpriseService.class);
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        show(remoteDrugService.findSupportDep(Arrays.asList(1),drugsEnterpriseDAO.getByAccount("ysq")));
    }

    public void show(Object object) {
        Assert.notNull(object,"object can't be null...");
        System.out.println(JSONUtils.toString(object));
    }
}
