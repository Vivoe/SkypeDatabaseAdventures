import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SkypeDataBase {

	/**
	 * @param args
	 * @throws ClassNotFoundException
	 */
	public static void main(String[] args) throws ClassNotFoundException {
		//Trying to make regex from list of faces from file. not working.
		System.out.println(genRegex());
		
		Pattern ignore = Pattern.compile("<.+>");

		Class.forName("org.sqlite.JDBC");

		Connection connection = null;
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:main.db");
			Statement statement = connection.createStatement();
			statement.setQueryTimeout(1000);

			ResultSet rs = statement.executeQuery("SELECT convo_id, body_xml FROM Messages");

			HashMap<Integer, Percentile> results = new HashMap<Integer, Percentile>();
			int totalCount = 0;
			while (rs.next()) {
				totalCount++;
				Integer convo = rs.getInt(1);
				String body = rs.getString(2);
				//That matches faces, apparently.
				if (body != null && !body.matches("<.*>")) {

					if (!results.containsKey(convo)) {
						results.put(convo, new Percentile(convo));
					}

					if (body.matches(".*(:p|:P|;P|:D|LD|:d|\\(:|\\(L|:\\)|;\\)|xD|XD|Xd|xd).*")) {
						results.get(convo).addCount(true);
					} else {
						results.get(convo).addCount(false);
					}
				}
			}
			System.out.println(totalCount);
			System.out.println(results);

			ResultSet names = statement
					.executeQuery("SELECT DISTINCT convo_id, dialog_partner FROM Messages WHERE dialog_partner IS NOT NULL AND TRIM(dialog_partner) <> ''");
			setNames(results, names);

			ArrayList<Percentile> list = sortMapByName(results);
			mergeDuplicates(list);
			Collections.sort(list);
			for (Percentile p : list) {
				System.out.println(p.name + " " + p.getPercent());
			}

		} catch (SQLException e) {
			System.err.println("SQLException:");
			System.err.println(e.getMessage());
		} finally {
			try {
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				System.err.println("Closing connection err:");
				System.err.println(e);
			}
		}
	}

	public static String genRegex() {
		Scanner in;
		try {
			in = new Scanner(new File("faces.txt"));
			String regex = "";
			while (in.hasNextLine()) {
				regex += in.nextLine().replace(" ", "|").replace("\\)", "\\\\)").replace("\\(", "\\\\(")+"|";
			}
			in.close();
			return regex.substring(0, regex.length()-1);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}

	}

	public static void setNames(HashMap<Integer, Percentile> a, ResultSet names) throws SQLException {
		while (names.next()) {
			Integer id = names.getInt(1);
			if (a.containsKey(id)) {
				a.get(id).name = names.getString(2);
			}
		}
	}

	public static void printSortedMap(HashMap<Integer, Percentile> a) {
		ArrayList<Percentile> b = new ArrayList<Percentile>(a.values());
		Collections.sort(b);
		for (Percentile p : b) {
			System.out.println(p.name + " " + p.getPercent());
		}
	}

	public static ArrayList<Percentile> sortMapByValue(HashMap<Integer, Percentile> a) {
		ArrayList<Percentile> b = new ArrayList<Percentile>(a.values());
		Collections.sort(b);
		return b;
	}

	public static ArrayList<Percentile> sortMapByName(HashMap<Integer, Percentile> a) {
		ArrayList<Percentile> b = new ArrayList<Percentile>(a.values());
		Collections.sort(b, new Comparator<Percentile>() {

			public int compare(Percentile o1, Percentile o2) {
				return o1.name.compareTo(o2.name);
			}
		});

		return b;

	}

	public static ArrayList<Percentile> mergeDuplicates(ArrayList<Percentile> a) {
		for (int i = 1; i < a.size(); i++) {
			String name1 = a.get(i - 1).name;
			if (!name1.equals("") && name1.equals(a.get(i).name)) {
				a.get(i - 1).count += a.get(i).count;
				a.get(i - 1).valid += a.get(i).valid;
				a.remove(i);
				i--;
			}
		}
		return a;
	}

}

class Percentile implements Comparable<Percentile> {
	String name = "";
	Integer id;
	int count = 0;
	int valid = 0;

	public Percentile(Integer id) {
		this.id = id;
	}

	public void addCount(boolean valid) {
		count++;
		if (valid) {
			this.valid++;
		}
	}

	public double getPercent() {
		return 100 * (valid / (double) count);
	}

	@Override
	public String toString() {
		return String.valueOf(getPercent());
	}

	public int compareTo(Percentile p) {
		if (getPercent() > p.getPercent()) {
			return 1;
		} else if (getPercent() < p.getPercent()) {
			return -1;
		} else {
			return 0;
		}
	}

}
