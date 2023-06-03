# SQLVisTerm

## **SQL Terminal Client (JDBC) With Data Visualization**

Few months ago I was looking for a simple SQL terminal client which can do some basic visualization of the data as well. But found none and hence developed this. It uses Jline3 and its example code to provide standard terminal functionality while the database communication and rendering of the results is implemented by the program. It is reasonably good for my own use but improvements are possible which I will be doing as needed.


### **System Requirements**

1. **Java 8** must be installed on your machine. I haven't tested it on higher versions of Java.
2. **lib** folder already contains 3 JDBC drivers (SQLite, MariaDB for MySQL, and AWS Redshift). If you want to connect to any other database then put the relevant JDBC driver in the lib folder.
3. **db** folder contains 2 sample SQLite databases (northwind.db and chinook.db)
4. **app.ini** in the root folder has SQLite connection parameters for northwind database. It also has commented-out connection parameters for AWS Redshift and MySQL with dummy values.


### **Downloading The Program**

Run below on the command prompt:

_git clone https://github.com/ahmedsaadpk/SQLVisTerm_


### **Running The Program**

On **Windows**, use **run.bat** present in the root folder. Either double-click it or open command prompt, then navigate to the project folder, type _run.bat_ and hit enter.

On **Linux**, use **run.sh** present in the root folder. Navigate to the project folder, type _./run.sh_ or _sh run.sh_ and hit enter.

By default it will be connected to SQLite database northwind. This can be changed by editing **app.ini** present in the root folder of the project. However northwind is a good database to demonstrate the features of SQLVisTerm.

When the program starts, you should see this on Windows:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/cb922f70-2da0-4652-9d48-e0058ed382cd)

and on Linux:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/71053f3e-1749-410d-b6cb-d44050ae9fdd)

Now enter the first SQL:

_Select ShipCountry, count(1) Total_Orders, Sum(Freight) Total_Freight, Avg(Freight) Avg_Freight From Orders Group By ShipCountry Order by Total_Orders Desc;_

It should produce the below output:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/ab66a067-14f5-4c4d-9d29-b27bcda8518a)

You can see that all numeric columns have colored bars to provide a quick idea of the comparitive size of the values in a column. The program uses 6 colors and if there is a 7th column with numeric values it will re-use the 1st color.

Sometimes visualization is not desirable like on the columns which contains numeric IDs. Like **EmployeeId** in this example:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/25d7360e-cd1c-427a-96d3-c8d20cd5ccac)

Here you can see that **EmployeeID** column also has bars. To prevent it, edit **app.ini** and add exclusion for this column as this:

_exclude=rank,year,id,employeeid_

Above configuration setting contains a comma separted list of column names on which bars are not desired.

Save **app.ini**, quit the program by typing **quit** or **exit**, and re-run it. Try the same SQL and now it shouldn't display bars in the EmployeeID column:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/cf1a4d32-72a6-48fa-a313-55c02fafdfc8)


### **Changing The Scale**

By default the scale of each column is 1.0 but you can change it. Type **scale 1.5** and hit enter. The last result will render again but the bars will be longer and the new scale will display under each column.

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/0f82c55a-0c09-40a8-925b-4047c461c691)

To hide the bars completely, type **scale 0** and hit enter. The result will be this:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/30def6fe-9ca5-41fc-bc98-9c99e5ecbdad)

Now to show the bars in **total_freight** column only, type **scale total_freight 2** (use the value of your choice) and hit enter. The result will be like this:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/77eb6f4d-6d08-4f19-aec0-23cb8c58878a)


### **Locking The Scale**

Sometimes you make minor SQL modifications to refine the results. By default the scale will reset to 1.0 after every modification. But you can prevent this by typing **lock scale** and hit enter. Now run the modified query, and the new result will follow the scale previously set for each column. But if a column is newly added in the modified query, it will use default scale 1.0.

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/859ae815-d258-4e2c-95b4-624172227400)

You can see in above example that the scale of each column is same as was in the previous screenshot.

### **Unlocking The Scale**

To revert the behavior of **lock scale**, type **unlock scale** and press enter.


### **Hiding Columns**

Sometimes you may need to hide few columns of the result. For this purpose use the **hide** command. For example to hide the column **total_orders** type **hide total_orders** and enter to show this result:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/4110c013-8193-462e-ba42-bee44afbbb34)

Note that the names of the hidden columns display under the results.

You can also hide multiple columns in single command like **hide total_orders total_freight** where the column names should be separated by space.


### **Unhiding Columns**

To unhide a column, use **unhide** command. For example **unhide total_orders**. Or to unhide multiple columns in one command use **unhide total_orders total_freight** where column names should be separated by space. Or to unhide all hidden columns use **unhide all**.


### **Locking Hidden Columns**

Sometimes you want that the hidden columns should remain hidden during query modifications. For this, use the command **lock hidden**, as below:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/389a11dd-6fd9-4e5c-944c-2065663c4714)

You can see that the column **total_orders** is still hidden even though it was a new SQL and results are different from previous one.


### **Unlocking Hidden Columns**

To undo the behavior of **lock hidden** use the command **unlock hidden**


### **Showing Statistics**

The program can display common statistics under each column which are **Min, Max, Mean, Standard Deviation, Variance and Population Variance**. To show these, type **stats on** and enter. Results will render again but this time these stats will also display under each column as this:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/c9570213-d4d7-4e45-9c13-ef8175943805)


### **Hiding Statistics**

Type **stats off** and hit enter.


### **Query Log**

All queries and their results are written to a file **query.log**. For now it's your responsibility to keep an eye on its size but in future file rolling will be implemented and when the size of the file will reach 1 MB, a new file will be created.


### **Adding Comments To Query Log**

You can tag queries and their results with comments. These comments are written to **query.log**. For this purpose use the **double-slash** syntax. For example type **//Order By Country** and enter. It will not be executed as a SQL query or a command, but it will be appended to query.log.

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/32d7e3e3-32f0-4325-a668-4c5d0fb848da)


### **Exit / Quit**

type **exit** or **quit** and enter.


### **Reading query.log**

In my experience, Linux **cat** command renders the bars correctly. Use it with **grep** to find something in it. For example:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/fe34b861-b0f8-45f1-b07b-c68453d38072)

Then:

![image](https://github.com/ahmedsaadpk/SQLVisTerm/assets/7383719/da961d74-b8f9-468c-a1bb-0bb25dbde6f3)
