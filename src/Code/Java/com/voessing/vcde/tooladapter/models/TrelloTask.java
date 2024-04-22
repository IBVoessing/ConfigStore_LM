package com.voessing.vcde.tooladapter.models;
import java.util.List;

public class TrelloTask {

	public Card card;

	public static class Card {
		public String name;
		public String desc;
		public String due;
		public String start;
		public String idList;
		public List<String> idMembers;
		public List<String> idLabels;
		
		public List<Checklist> checklists;
		
		public static class Checklist {
			public String name;
			public List<CheckItem> checkItems;
		}

		public static class CheckItem {
			public String name;
			public String due;
			public String idMember;
		}
	}
}
