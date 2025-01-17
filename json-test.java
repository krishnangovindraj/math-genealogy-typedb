package com.vaticle.typedb.example.genealogy.math_genealogy;

import com.vaticle.typeql.lang.query.TypeQLDelete;
import mjson.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import static mjson.Json.read;
import com.vaticle.typedb.client.api.TypeDBClient;
import com.vaticle.typedb.client.api.TypeDBSession;
import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typedb.client.TypeDB;
import com.vaticle.typeql.lang.TypeQL;

import static com.vaticle.typeql.lang.TypeQL.*;

import com.vaticle.typeql.lang.query.TypeQLInsert;

class json_test
{
    public static int get_id(Json file, int idx)
    {
        return file.at("nodes").at(idx).at("id").asInteger();
    }
    public static String get_name(Json file, int idx)
    {
        return file.at("nodes").at(idx).at("name").asString();
    }
    public static String get_school(Json file, int idx)
    {
        return  file.at("nodes").at(idx).at("school").asString();
    }
    public static int get_year(Json file, int idx)
    {
        if (!file.at("nodes").at(idx).at("year").isNull())
        return file.at("nodes").at(idx).at("year").asInteger();
        else return 0;
    }
    public static String get_speciality(Json file, int idx) {
        if (!file.at("nodes").at(idx).at("subject").isNull())
        {
            return file.at("nodes").at(idx).at("subject").asString();
        }
        else return "Unknown";
    }
    public static List get_advisors(Json file, int idx) {
        List<Object> ls = file.at("nodes").at(idx).at("advisors").asList();
        return ls;
    }


    public static void main(String[] args)
    {
        Path filePath=Paths.get("genealogy/math_genealogy/datalite.json");

        TypeDBClient client = TypeDB.coreClient("localhost:1729");
        try
        {
            String contents;
            contents=Files.readString(filePath);
            Json file=read(contents);
            List mylist=file.at("nodes").asList();
            int sz=mylist.size();
            System.out.println("Size = "+sz);

            try (TypeDBSession session = client.session("onboarding", TypeDBSession.Type.DATA)) {

                //first purge existing data
                try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE))
                {
                    TypeQLDelete query = TypeQL.match(
                            var("p").isa("thing")
                    ).delete(var("p").isa("thing"));
                    writeTransaction.query().delete(query);
                    // to persist changes, a write-transaction must always be committed (closed)
                    writeTransaction.commit();
                }

                //for instantiating mathematicians
                for (int z = 0; z < sz; z++) {
                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                        // Insert a person using a WRITE transaction

                        TypeQLInsert insertQuery = TypeQL.insert
                                (var("x").isa("mathematician")
                                        .has("id", get_id(file, z))
                                        .has("name", get_name(file, z))
                                        .has("year_of_degree", get_year(file, z))
                                );
                        writeTransaction.query().insert(insertQuery);
                        // to persist changes, a write-transaction must always be committed (closed)
                        writeTransaction.commit();
                        System.out.println("mathematician instantiated");
                    }
                }

                //for instantiating schools
                Set<String> school_set = new HashSet<String>();
                for (int z = 0; z < sz; z++) {
                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                        // Insert a person using a WRITE transaction
                        String school=get_school(file, z);

                        if (!school_set.contains(school)) {
                            school_set.add(school);
                            TypeQLInsert insertQuery = TypeQL.insert
                                    (var("x").isa("school")
                                            .has("name", school)
                                    );
                            writeTransaction.query().insert(insertQuery);
                            writeTransaction.commit();
                        }

                        System.out.println(school + " school instantiated");
                    }
                }



                //for instantiating subjects
                try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                    // Insert a person using a WRITE transaction

                    TypeQLInsert insertQuery = TypeQL.insert
                            (var("x").isa("subject")
                                    .has("name", "mathematics")
                            );
                    writeTransaction.query().insert(insertQuery);
                    writeTransaction.commit();
                }
                Set<ArrayList<String>> offerSet=new HashSet<>();
                //for instantiating specialities
                Set<String> speciality_set = new HashSet<String>();
                for (int z = 0; z < sz; z++) {
                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                        // Insert a person using a WRITE transaction
                        String speciality=get_speciality(file, z);

                        if (!speciality_set.contains(speciality)) {
                            speciality_set.add(speciality);
                            TypeQLInsert insertQuery = TypeQL.insert
                                    (var("x").isa("speciality")
                                            .has("name", speciality)
                                    );
                            writeTransaction.query().insert(insertQuery);
                            writeTransaction.commit();
                        }
                        System.out.println(speciality + " speciality instantiated");
                    }
                }
                //for instantiating all relationships
                for (int z = 0; z < sz; z++)
                {
                    int m_id=get_id(file, z);
                    String school=get_school(file, z);

                    //add studentship relationship
                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE))
                    {
                        TypeQLInsert insertQuery = TypeQL.match(
                                var("m1").isa("mathematician").has("id", m_id),
                                var("s").isa("school").has("name", school)
                        ).insert(
                                var("new-studentship").rel("student", "m1").rel("school", "s").isa("studentship")
                        );
                        writeTransaction.query().insert(insertQuery);
                        writeTransaction.commit();
                    }


                    //offer relationship
                    String speciality=get_speciality(file, z);

                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE))
                    {
                        ArrayList<String> a=new ArrayList<>();
                        a.add(school);
                        a.add("mathematics");
                        a.add(speciality);
                        if (!offerSet.contains(a))
                        {
                            TypeQLInsert insertQuery = TypeQL.match(
                                    var("s").isa("school").has("name", school),
                                    var("s2").isa("subject").has("name", "mathematics"),
                                    var("s3").isa("speciality").has("name", speciality)
                            ).insert(
                                    var("new-offer").rel("school", "s").rel("subject", "s2").rel("speciality", "s3").isa("offer")
                            );
                            offerSet.add(a);
                            writeTransaction.query().insert(insertQuery);
                            writeTransaction.commit();
                            System.out.println("Offer Relationship added\n"+" school: "+school+" speciality = "+speciality);
                            System.out.println("size = "+offerSet.size());
                        }
                    }

                    //add mastery relationship
                    try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE))
                    {
                        TypeQLInsert insertQuery = TypeQL.match(
                                var("m").isa("mathematician").has("id", m_id),
                                var("s").isa("speciality").has("name", speciality)
                        ).insert(
                                var("new-mastery").rel("master", "m").rel("speciality", "s").isa("mastery")
                        );
                        writeTransaction.query().insert(insertQuery);
                        writeTransaction.commit();
                    }

                    List advisor_list=get_advisors(file, z);
                    int len=advisor_list.size();

                    //add research relationship
                    for (int j=0; j<len; j++)
                    {
                        java.lang.Long a_id=(java.lang.Long)advisor_list.get(j);
                        //research relationship
                        try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE))
                        {
                            TypeQLInsert insertQuery = TypeQL.match(
                                    var("m1").isa("mathematician").has("id", m_id),
                                    var("m2").isa("mathematician").has("id", a_id),
                                    var("s").isa("school").has("name", school)
                            ).insert(
                                    var("new-research").rel("advisor", "m2").rel("advisee", "m1").rel("school", "s").isa("research")
                            );
                            writeTransaction.query().insert(insertQuery);
                            writeTransaction.commit();
                        }
                    }
                }
            }
        }
        catch(IOException e)
        {
            System.out.println("IO Exception in file: "+e.getMessage());
            e.printStackTrace();
        }
        client.close();
    }
}

