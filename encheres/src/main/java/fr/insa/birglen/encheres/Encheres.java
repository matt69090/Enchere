/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package fr.insa.birglen.encheres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** BONJOUR
 * select et insert pour les tables
 * @author acoulibaly01
 */
public class Encheres {

    public static Connection connectGeneralPostGres(String host,
            int port, String database,
            String user, String pass)
            throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection con = DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + port
                + "/" + database,
                user, pass);
        con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        return con;
    }

    public static Connection defautConnect()
            throws ClassNotFoundException, SQLException {
        return connectGeneralPostGres("localhost", 5439, "postgres", "postgres", "pass");
    }
    
        public static class NomExisteDejaException extends Exception {
    }

    // 
    // lors de la crÃ©ation d'un utilisateur, l'identificateur est automatiquement
    // crÃ©Ã© par le SGBD.
    // on va souvent avoir besoin de cet identificateur dans le programme,
    // par exemple pour gÃ©rer des liens "aime" entre utilisateur
    // vous trouverez ci-dessous la faÃ§on de rÃ©cupÃ©rer les identificateurs
    // crÃ©Ã©s : ils se prÃ©sentent comme un ResultSet particulier.

    public static void creeSchemaEnchere(Connection con)
            throws SQLException {
        // je veux que le schema soit entierement crÃ©Ã© ou pas du tout
        // je vais donc gÃ©rer explicitement une transaction
        con.setAutoCommit(false);
        try ( Statement st = con.createStatement()) {
            // creation des tables
            st.executeUpdate(
                    """
                    create table Enchere (
                        id integer not null primary key
                        generated always as identity,
                        nom varchar(100) not null unique,
                        reserve float,
                        description_courte varchar(500),
                        description_detaille varchar(100000),
                        categorie varchar(50),
                        jour_de_fin_denchere integer not null,
                        mois_de_fin_denchere integer not null,
                        annee_de_fin_denchere integer not null
                    )
                    """);
            // si j'arrive jusqu'ici, c'est que tout s'est bien passÃ©
            // je confirme (commit) la transaction
            con.commit();
            // je retourne dans le mode par dÃ©faut de gestion des transaction :
            // chaque ordre au SGBD sera considÃ©rÃ© comme une transaction indÃ©pendante
            con.setAutoCommit(true);
        } catch (SQLException ex) {
            // quelque chose s'est mal passÃ©
            // j'annule la transaction
            con.rollback();
            // puis je renvoie l'exeption pour qu'elle puisse Ã©ventuellement
            // Ãªtre gÃ©rÃ©e (message Ã  l'utilisateur...)
            throw ex;
        } finally {
            // je reviens Ã  la gestion par dÃ©faut : une transaction pour
            // chaque ordre SQL
            con.setAutoCommit(true);
        }
    }
    
    public static void creeSchemaClient(Connection con)
            throws SQLException {
        con.setAutoCommit(false);
        try ( Statement st = con.createStatement()) {
            st.executeUpdate(
                    """
                    create table Clients (
                        id integer not null primary key
                        generated always as identity,
                        nom varchar(30) not null unique,
                        pass varchar(30) not null,
                        role integer not null
                        )
                        """);                  
            con.commit();
            con.setAutoCommit(true);
        } catch (SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
    }

    public static void creeSchemaAdmin(Connection con)
            throws SQLException {
        con.setAutoCommit(false);
        try ( Statement st = con.createStatement()) {
            st.executeUpdate(
                    """
                    create table Admin (
                        id integer not null primary key
                        generated always as identity,
                        nom varchar(30) not null unique,
                        pass varchar(30) not null
                    )
                    """);                 
            con.commit();
            con.setAutoCommit(true);
        } catch (SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
    }
    
    public static int createClient(Connection con, String nom, String pass, int roleID)
            throws SQLException, NomExisteDejaException {
        // je me place dans une transaction pour m'assurer que la sÃ©quence
        // test du nom - crÃ©ation est bien atomique et isolÃ©e
        con.setAutoCommit(false);
        try ( PreparedStatement chercheNom = con.prepareStatement(
                "select id from Clients where nom = ?")) {
            chercheNom.setString(1, nom);
            ResultSet testNom = chercheNom.executeQuery();
            if (testNom.next()) {
                throw new NomExisteDejaException();
            }
            // lors de la creation du PreparedStatement, il faut que je prÃ©cise
            // que je veux qu'il conserve les clÃ©s gÃ©nÃ©rÃ©es
            try ( PreparedStatement pst = con.prepareStatement(
                    """
                insert into Clients (nom,pass,role) values (?,?,?)
                """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pst.setString(1, nom);
                pst.setString(2, pass);
                pst.setInt(3, roleID);
                pst.executeUpdate();
                con.commit();

                // je peux alors rÃ©cupÃ©rer les clÃ©s crÃ©Ã©es comme un result set :
                try ( ResultSet rid = pst.getGeneratedKeys()) {
                    // et comme ici je suis sur qu'il y a une et une seule clÃ©, je
                    // fait un simple next 
                    rid.next();
                    // puis je rÃ©cupÃ¨re la valeur de la clÃ© crÃ©Ã© qui est dans la
                    // premiÃ¨re colonne du ResultSet
                    int id = rid.getInt(1);
                    return id;
                }
            }
        } catch (Exception ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
    }
    
    public static int createEnchere(Connection con, String nom ,int reserve, String description_courte,String description_detaille, String categorie, int jour_de_fin_denchere, int mois_de_fin_denchere, int annee_de_fin_denchere)
            throws SQLException, NomExisteDejaException {
        // je me place dans une transaction pour m'assurer que la sÃ©quence
        // test du nom - crÃ©ation est bien atomique et isolÃ©e
        con.setAutoCommit(false);
        try ( PreparedStatement chercheNom = con.prepareStatement(
                "select id from enchere where nom = ?")) {
            chercheNom.setString(1, nom);
            ResultSet testNom = chercheNom.executeQuery();
            if (testNom.next()) {
                throw new NomExisteDejaException();
            }
            // lors de la creation du PreparedStatement, il faut que je prÃ©cise
            // que je veux qu'il conserve les clÃ©s gÃ©nÃ©rÃ©es
            try ( PreparedStatement pst = con.prepareStatement(
                    """
                insert into enchere (nom,reserve,description_courte,description_detaille,categorie,jour_de_fin_denchere,mois_de_fin_denchere,annee_de_fin_denchere) values (?,?,?,?,?,?,?,?)
                """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                pst.setString(1, nom);
                pst.setInt(2, reserve);
                pst.setString(3, description_courte);
                pst.setString(4, description_detaille);
                pst.setString(5, categorie);
                pst.setInt(6, jour_de_fin_denchere);
                pst.setInt(7, mois_de_fin_denchere);
                pst.setInt(8, annee_de_fin_denchere);
                pst.executeUpdate();
                con.commit();

                // je peux alors rÃ©cupÃ©rer les clÃ©s crÃ©Ã©es comme un result set :
                try ( ResultSet rid = pst.getGeneratedKeys()) {
                    // et comme ici je suis sur qu'il y a une et une seule clÃ©, je
                    // fait un simple next 
                    rid.next();
                    // puis je rÃ©cupÃ¨re la valeur de la clÃ© crÃ©Ã© qui est dans la
                    // premiÃ¨re colonne du ResultSet
                    int id = rid.getInt(1);
                    return id;
                }
            }
        } catch (Exception ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
    }
    
    public static void afficheTousLesClients(Connection con) throws SQLException {
        try ( Statement st = con.createStatement()) {
            // pour effectuer une recherche, il faut utiliser un "executeQuery"
            // et non un "executeUpdate".
            // un executeQuery retourne un ResultSet qui contient le rÃ©sultat
            // de la recherche (donc une table avec quelques information supplÃ©mentaire)
            try ( ResultSet tlu = st.executeQuery("select id,nom,pass,role from clients")) {
                // un ResultSet se manipule un peu comme un fichier :
                // - il faut le fermer quand on ne l'utilise plus
                //   d'oÃ¹ l'utilisation du try(...) ci-dessus
                // - il faut utiliser la mÃ©thode next du ResultSet pour passer
                //   d'une ligne Ã  la suivante.
                //   . s'il y avait effectivement une ligne suivante, next renvoie true
                //   . si l'on Ã©tait sur la derniÃ¨re ligne, next renvoie false
                //   . au dÃ©but, on est "avant la premiÃ¨re ligne", il faut donc
                //     faire un premier next pour accÃ©der Ã  la premiÃ¨re ligne
                //     Note : ce premier next peut renvoyer false si le rÃ©sultat
                //            du select Ã©tait vide
                // on va donc trÃ¨s souvent avoir un next
                //   . dans un if si l'on veut tester qu'il y a bien un rÃ©sultat
                //   . dans un while si l'on veut traiter l'ensemble des lignes
                //     de la table rÃ©sultat

                System.out.println("liste des utilisateurs :");
                System.out.println("------------------------");
                // ici, on veut lister toutes les lignes, d'oÃ¹ le while
                while (tlu.next()) {
                    // Ensuite, pour accÃ©der Ã  chaque colonne de la ligne courante,
                    // on a les mÃ©thode getInt, getString... en fonction du type
                    // de la colonne.

                    // on peut accÃ©der Ã  une colonne par son nom :
                    int id = tlu.getInt("id");
                    // ou par son numÃ©ro (la premiÃ¨re colonne a le numÃ©ro 1)
                    String nom = tlu.getString(2);
                    String pass = tlu.getString("pass");
                    String mess = id + " : " + nom + " (" + pass + ")" +"\n";
                    int idRole = tlu.getInt("role");
                    if (idRole == 1) {
                        mess = mess + " --> admin";
                    }
                    System.out.println(mess);
                }
            }
        }

    }
    
    
    public static void afficheToutesLesEncheres(Connection con) throws SQLException {
        try ( Statement st = con.createStatement()) {
            try ( ResultSet tlu = st.executeQuery("""
                                                  select id,nom,reserve,description_courte,description_detaille,
                                                  categorie,jour_de_fin_denchere,mois_de_fin_denchere,
                                                  annee_de_fin_denchere from enchere
                                                                                                  """)) {
                System.out.println("liste des encheres :");
                System.out.println("------------------------");
                
                while (tlu.next()) {
                   
                    int id = tlu.getInt("id");
                    // ou par son numÃ©ro (la premiÃ¨re colonne a le numÃ©ro 1)
                    String nom = tlu.getString(2);
                    int reserve = tlu.getInt(3);
                    String description_courte = tlu.getString(4);
                    String description_detaille = tlu.getString(5);
                    String categorie = tlu.getString(6);
                    
                    int jour_de_fin_denchere = tlu.getInt(7);
                    int mois_de_fin_denchere = tlu.getInt(8);
                    int annee_de_fin_denchere = tlu.getInt(9);
                    
                    
                    
                    String mess = id + " : \n" + categorie + " :" + "\n"+"\n" + nom + " (" + jour_de_fin_denchere + "/" + mois_de_fin_denchere + "/" + annee_de_fin_denchere + ")" 
                            + "\n"+ "\n"+ description_courte + "\n" + "\n"+ "\n" + description_detaille
                            + "\n"+ "\n" + "reserve = " + reserve + "$" + "\n";
                    System.out.println(mess);
                }
            }
        }

    }
    
    public static void deleteSchemaClient(Connection con) throws SQLException {
        try ( Statement st = con.createStatement()) {
            // pour Ãªtre sÃ»r de pouvoir supprimer, il faut d'abord supprimer les liens
            // puis les tables
            // suppression des liens, attention aux contraintes
            
            
            
            // je peux maintenant supprimer les tables
            try {
                st.executeUpdate(
                        """
                    drop table clients
                    """);
                System.out.println("table clients dropped");
            } catch (SQLException ex) {
                System.out.println("table clients déjà inexistante");
            }
        }
    }
    
    

    public static void main(String[] args) {
        
        try {
            Connection con = defautConnect();
            System.out.println("Connection OK");
            deleteSchemaClient(con);
            System.out.println("délition de la table Enchere OK");
        } catch (ClassNotFoundException ex) {
            throw new Error(ex);
        } catch (SQLException ex) {
            System.out.println("la table Enchere est déjà supprimée");
        }
        
        //création de la table enchère
        try {
            Connection con = defautConnect();
            System.out.println("Connection OK");
            creeSchemaEnchere(con);
            System.out.println("creation de la table Enchere OK");
        } catch (ClassNotFoundException ex) {
            throw new Error(ex);
        } catch (SQLException ex) {
            System.out.println("la table Enchere est déjà créée");
        }
        
        //création de la table client
        try {
            Connection con = defautConnect();
            System.out.println("Connection OK");
            creeSchemaClient(con);
            System.out.println("creation de la table Client OK");
        } catch (ClassNotFoundException ex) {
            throw new Error(ex);
        } catch (SQLException ex) {
            System.out.println("la table Client est déjà créée");
        }
        
        //création de la table admin
        try {
            Connection con = defautConnect();
            System.out.println("Connection OK");
            creeSchemaAdmin(con);
            System.out.println("creation de la table Admin OK");
        } catch (ClassNotFoundException ex) {
            throw new Error(ex);
        } catch (SQLException ex) {
            System.out.println("la table Admin est déjà créée");
        }
        
        //ajout des utilisateurs
        try {
            Connection con = defautConnect();
            createClient(con, "matt69", "mbirglen01",1);
            createClient(con, "Suliman", "Samini01",1);
            createClient(con, "Mr_Bean", "BoB",2);
        } catch (NomExisteDejaException ex) {
            System.out.println("le nom de(s) utilisateur(s) existe(nt) déjà, la création n'a pas pu avoir lieu");
        } catch (ClassNotFoundException ex) {
            System.out.println("classe createClient non trouvée");
        } catch (SQLException ex) {
            System.out.println("la table client n'a pas pu être modifiée");
        }
        
        //ajout d'une enchère
        try {
            Connection con = defautConnect();
            createEnchere(con, "M3",20000,"E90 4-door maintained M3","ce véhicule a toujours été entretenu par BMW, équipements disponibles :"
                    + "toit ouvrant, GPS, DKG gearbox","cars",10,1,2023);
        } catch (NomExisteDejaException ex) {
            System.out.println("le nom de l'enchère crée existe déjà, la création n'a pas pu avoir lieu");
        } catch (ClassNotFoundException ex) {
            System.out.println("classe createEnchere non trouvée");
        } catch (SQLException ex) {
            System.out.println("la table Enchere n'a pas pu être modifiée");
        }        
        
        //affichage de tous les utilisateurs
        try {
            Connection con = defautConnect();
            afficheTousLesClients(con);
        } catch (ClassNotFoundException ex) {
            System.out.println("classe afficheTousLesClients non trouvée");
        } catch (SQLException ex) {
            System.out.println("la table client n'a pas pu être affichée");
        }
        
        //affichage des encheres
        try {
            Connection con = defautConnect();
            afficheToutesLesEncheres(con);
        } catch (ClassNotFoundException ex) {
            System.out.println("classe afficheToutesLesEncheres non trouvée");
        } catch (SQLException ex) {
            System.out.println("la table enchere n'a pas pu être affichée");
        }
        
        

                
    }
}
