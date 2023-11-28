
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Jul  2 19:49:36 EDT 2023
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Example Database: Bank Database
 */

package scalation
package database
package table

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `bankDB` main function uses the `Table` class for simple database application.
 *  > runMain scalation.database.table.bankDB
 */
@main def bankDB (): Unit =

    val branch   = Table ("branch", "bname, assets, bcity", "S, D, S", "bname")
    val customer = Table ("customer", "cname, street, ccity", "S, S, S", "cname")
    val deposit  = Table ("deposit", "bname, accno, cname, balance", "S, I, S, D", "accno")
    val loan     = Table ("loan", "bname, loanno, cname, amount", "S, I, S, D", "loanno")

    branch.add ("Main",     15000000.0, "Athens")
          .add ("Lake",     20000000.0, "Gainesville")
          .add ("Downtown", 10000000.0, "Winder")
          .add ("Alps",     11000000.0, "Athens" )
          .show ()

    customer.add ("Peter", "Maple St", "Athens")
            .add ("Paul",  "Oak St",   "Athens")
            .add ("Mary",  "Elm St",   "Winder")
            .add ("Joe",   "Pine St",  "Athens")
            .show ()

    deposit.add ("Downtown", 901, "Peter", 1000.0)
           .add ("Main",     902, "Paul",  2000.0)
           .add ("Alps",     903, "Paul",  3000.0)
           .add ("Lake",     904, "Paul",  1000.0)
           .add ("Main",     905, "Mary",  1000.0)
           .add ("Alps",     906, "Mary",  2000.0)
           .add ("Lake",     907, "Joe",   1500.0)
           .show ()

    loan.add ("Lake",     1001, "Peter", 1000.0)
        .add ("Alps",     1002, "Peter", 2000.0)
        .add ("Main",     1003, "Paul",  1000.0)
        .add ("Alps",     1004, "Paul",  2000.0)
        .add ("Main",     1005, "Mary",  1000.0)
        .add ("Downtown", 1006, "Mary",  2000.0)
        .show ()

    branch.create_index ()
    customer.create_index ()
    deposit.create_index ()
    loan.create_index ()

//  a) List the names and cities of customers who have at least one loan covered by one of their accounts (Silver Members).

    banner ("""Q1: (customer ⋈ deposit ⋈ loan).π("cname, ccity")""")
    val q1 = (customer ⋈ deposit ⋈ loan).π("cname, ccity")
    q1.create_index ()
    q1.show ()

//  b) List the names and cities of customers who have accounts at all the branches located in the city they live in.

    banner ("my_branches: cname, ccity and their branches")
    val my_branches = (customer ⋈ deposit ⋈ branch).π("cname, ccity, bname")
    my_branches.show ()

    banner ("city_branches: cname, ccity and branches in their city")
    val city_branches = (customer × branch).σ ("ccity == bcity").π("cname, ccity, bname")
    city_branches.show ()

    banner ("Q2: customers with accounts at all branches in their city ")
    val q2 = customer.π("cname, ccity") - (city_branches - my_branches).π("cname, ccity")
    q2.show ()

    val q2_ = customer.π("cname, ccity") - ( (customer × branch).σ ("ccity == bcity").π("cname, ccity, bname") -
                                           (customer ⋈ deposit ⋈ branch).π("cname, ccity, bname") ).π("cname, ccity")
    q2_.show ()

end bankDB


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `bankDB2` main function uses the `Table` class for simple database application.
 *  > runMain scalation.database.table.bankDB2
 */
@main def bankDB2 (): Unit =

    val branch    = Table ("branch", "bname, bcity, assets", "S, S, D", "bname")
    val customer  = Table ("customer", "id, cname, street, ccity", "I, S, S, S", "id")
    val loan      = Table ("loan", "lnumber, bname, amount", "I, S, D", "lnumber")
    val borrower  = Table ("borrower", "id, lnumber", "I, I", "id, lnumber")
    val account   = Table ("account", "anumber, bname, balance", "I, S, D", "anumber")
    val depositor = Table ("depositor", "id, anumber", "I, I", "id, anumber")

    branch.add ("Main",     "Athens",      15000000.0)
          .add ("Lake",     "Gainesville", 20000000.0)
          .add ("Downtown", "Winder",      10000000.0)
          .add ("Alps",     "Athens",      11000000.0)
          .show ()

    customer.add (101, "Peter", "Maple St", "Athens")
            .add (102, "Paul",  "Oak St",   "Athens")
            .add (103, "Mary",  "Elm St",   "Winder")
            .add (104, "Joe",   "Pine St",  "Athens")
            .show ()

//  Peter: Lake, Alps -- Downtown
//  Paul:  Main, Alps -- Main, Alps, Lake
//  Mary:  Main, Downtown -- Main, Alps 

    loan.add (1001, "Lake",     1000.0)
        .add (1002, "Alps",     2000.0)
        .add (1003, "Main",     1000.0)
        .add (1004, "Alps",     2000.0)
        .add (1005, "Main",     1000.0)
        .add (1006, "Downtown", 2000.0)
        .show ()

    borrower.add (101, 1001)
            .add (101, 1002)
            .add (102, 1003)
            .add (102, 1004)
            .add (103, 1005)
            .add (103, 1006)
            .show ()

    account.add (901, "Downtown", 1000.0)
           .add (902, "Main",     2000.0)
           .add (903, "Alps",     3000.0)
           .add (904, "Lake",     1000.0)
           .add (905, "Main",     1000.0)
           .add (906, "Alps",     2000.0)
           .add (907, "Lake",     1500.0)
           .show ()

    depositor.add (101, 901)
             .add (102, 902)
             .add (102, 903)
             .add (102, 904)
             .add (103, 905)
             .add (103, 906)
             .add (104, 907)
             .show ()

    branch.create_index ()
    customer.create_index ()
    loan.create_index ()
    borrower.create_index ()
    account.create_index ()
    depositor.create_index ()

    branch.show_index ()
    customer.show_index ()
    loan.show_index ()
    borrower.show_index ()
    account.show_index ()
    depositor.show_index ()

    banner ("""customer ⋈ borrower ⋈ loan""")
    (customer ⋈ borrower ⋈ loan).show ()
    val tl = (customer ⋈ borrower ⋈ loan).π("id, cname, bname")
    tl.show ()

    banner ("""customer ⋈ depositor ⋈ account""")
    val ta = (customer ⋈ depositor ⋈ account).π("id, cname, bname")
    ta.show ()

    banner ("Customers with Loans")
    val t_all = (borrower ⋈ customer).π("id, cname")
    t_all.create_index ()
    t_all.show ()

    banner ("Silver Members")
    val t_silver = (tl ⋂ ta).π("id, cname")
    t_silver.create_index ()
    t_silver.show ()

    banner ("Silver Members 2")
    val t_silver2 = (customer ⋈ loan ⋈ borrower ⋈ account ⋈ depositor).π("id, cname")
    t_silver2.create_index ()
    t_silver2.show ()

    banner ("Gold Members")
    val t_gold = (t_all - (tl - ta).π("id, cname"))
    t_gold.create_index ()
    t_gold.show ()

    banner ("Gold Members 2")
    val t_gold2 = (customer ⋈ (borrower.π("id") - (
                                  (loan ⋈ borrower).π("id, bname") -
                                  (account ⋈ depositor).π("id, bname")).π("id") )).π("id, cname")
    t_gold2.create_index ()
    t_gold2.show ()

end bankDB2

