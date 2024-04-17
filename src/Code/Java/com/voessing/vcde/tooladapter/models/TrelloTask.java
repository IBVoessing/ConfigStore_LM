package com.voessing.vcde.tooladapter.models;
import java.util.List;

public class TrelloTask {

	public Card card;

	static class Card {
		public String name;
		public String desc;
		public String due;
		public String start;
		public List<String> idMembers;
		
		public List<Checklist> checklists;
		
		static class Checklist {
			public String name;
			public List<CheckItem> checkItems;
		}

		static class CheckItem {
			public String name;
			public String due;
			public String idMember;
		}
	}
}
