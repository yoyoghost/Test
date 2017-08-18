/*package test.mongo;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import junit.framework.TestCase;
import org.bson.BSONObject;
import org.bson.Document;

import java.util.Map;
import java.util.Set;

*//**
 * Created by sean on 15/6/15.
 *//*
public class MongodbTester1 extends TestCase{

    public void testMongodbConnect(){
        MongoClientURI uri = new MongoClientURI("mongodb://mongoadmin:551373@121.43.186.207/");
        MongoClient m = new MongoClient(uri);

        MongoDatabase db = m.getDatabase("eh");

        MongoCollection<Document> hospital = db.getCollection("hospital");

        hospital.createIndex(new BasicDBObject("loc", "2dsphere"));


        if(hospital.count() == 0) {

            BasicDBList coordinates = new BasicDBList();
            coordinates.put(0, 120.184543);
            coordinates.put(1, 30.263314);
            Document doc = new Document("name", "浙一").append("loc", new BasicDBObject("type", "Point").append("coordinates",coordinates));
            hospital.insertOne(doc);

            coordinates.put(0,120.183598);
            coordinates.put(1,30.256849);
            doc = new Document("name", "浙二").append("loc", new BasicDBObject("type", "Point").append("coordinates",coordinates));
            hospital.insertOne(doc);

            coordinates.put(0,120.185246);
            coordinates.put(1,30.251889);
            doc = new Document("name", "市三").append("loc", new BasicDBObject("type", "Point").append("coordinates",coordinates));
            hospital.insertOne(doc);

            coordinates.put(0,120.173596);
            coordinates.put(1,30.260117);
            doc = new Document("name", "市一").append("loc", new BasicDBObject("type", "Point").append("coordinates",coordinates));
            hospital.insertOne(doc);

            coordinates.put(0,120.208537);
            coordinates.put(1,30.262558);
            doc = new Document("name", "邵逸夫").append("loc", new BasicDBObject("type", "Point").append("coordinates",coordinates));
            hospital.insertOne(doc);


        }
        else{

            System.out.println(hospital.find(
                    Document.parse("{loc:{$near:" +
                            "{$geometry:{type:'point',coordinates:[120.209986,30.259691]},$maxDistance:5000}}}")
            ).first());
        }

    }
}
*/