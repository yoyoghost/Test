package test;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class MyJsonRequesterTester extends AbstractJUnit4JsonRequest {

    @Test
    public void getUser(){
        String userId = "15869197716";
        Map<String,Object> result = exec("eh.userRemoteLoader","load", ImmutableList.<Object>of(userId), "21932805-c2ff-4b70-b9b5-7d6edc548722");
        System.out.println(result);
        Assert.assertEquals(200, result.get("code"));
        Assert.assertEquals(userId, ((Map)result.get("body")).get("id"));
    }

}
