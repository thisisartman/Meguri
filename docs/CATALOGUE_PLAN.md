# Growing the catalogue

How Meguri's catalogue grows from three prefectures to all of Japan: prefecture by prefecture, then area by area and city by city. Follow the progress on the [project board](https://github.com/users/thisisartman/projects/2) and in [issue #5](https://github.com/thisisartman/Meguri/issues/5).

## Three levels

| Level | What it means | Example |
|---|---|---|
| **Prefecture** | What you download in Regions. A prefecture is "in Meguri" once it has a catalogue at all. | Niigata |
| **Area** | A group of cities inside a prefecture, the way people there talk about it. Used to plan the work and to show what's done. | Chūetsu (中越), Jōetsu (上越), Kaetsu (下越), Sado (佐渡) |
| **City** | A city, town or village. Most manhole cards and almost every goshuin are handed out at this level. | Nagaoka, Tsubame, Minamiuonuma |

## Three kinds of source

Different collectibles are published in different ways, so they're filled in differently.

**1. Nationwide lists: a whole prefecture at once.**
These have one official list for all of Japan, so a prefecture can be filled in completely in one go.
- **Manhole cards:** the GKP card list, by city
- **Dam cards:** the Ministry of Land, Infrastructure, Transport and Tourism (MLIT) list
- **Roadside stations (michi-no-eki):** the MLIT list

**2. Railway lists: line by line.**
Station stamps and Ekitag are published by the railway companies, and stamps are collected along a line, so they're added one line at a time with stations in order. Positions come from OpenStreetMap.
- **Station stamps:** JR East and other operators' stamp lists
- **Ekitag:** JR East's announcements

**3. No central list: city by city, together.**
Goshuin, museum stamps and local stamp rallies have no single list. These grow city by city, mostly from collectors' reports through the [Add a place or a collectible](https://github.com/thisisartman/Meguri/issues/new?template=new_place.yml) form, checked against the shrine's, temple's or museum's own page.

## When a prefecture is done

Each prefecture moves through three stages:

1. **Started:** at least one category is in, and it appears in Regions.
2. **Covered:** every nationwide list (manhole cards, dam cards, roadside stations) is complete, and every JR line in the prefecture has its station stamps in order.
3. **Filled in:** each area has its cities checked for goshuin and other local collectibles, at least the well-known ones.

Each prefecture has a checklist issue:

```
Prefecture: <name>
- [ ] Manhole cards (GKP list): every city
- [ ] Dam cards (MLIT)
- [ ] Roadside stations (MLIT)
- [ ] Station stamps: each line, stations in order
- [ ] Ekitag (if in JR East's area)
- [ ] Offline map built
- [ ] Areas: <area> (cities checked: …), <area> …
- [ ] Published in meguri-data
```

## Order

**Wave 1: finish what's started.**
Niigata, Saitama and Tokyo to "Covered", and Gunma beyond the Joetsu Line stations.

**Wave 2: the rest of JR East's area around them.**
Kanagawa, Chiba, Ibaraki, Tochigi and Nagano. Their stations are mostly on JR East's lists and the same tools work there, so each one is mostly reuse. Ekitag is added wherever JR East offers it.

**Wave 3: Tohoku.**
Fukushima, Yamagata, Miyagi, Akita, Iwate and Aomori, still on JR East's lists.

**Wave 4: the rest of Japan, by demand.**
Prefectures are taken in the order people ask for them in [issue #5](https://github.com/thisisartman/Meguri/issues/5) (👍 votes and reports), starting with the most visited: Kyoto, Osaka, Hokkaido, Okinawa, Nara, Hiroshima and Fukuoka. Each new railway company's stamp list is a one-time piece of work that then serves every prefecture it runs through.

A prefecture with many reports from collectors can jump the queue at any time.

## Where things are today

From the current region data:

| Prefecture | Manhole cards | Station stamps | Ekitag | Dam cards | Roadside stations | Goshuin | Offline map |
|---|---|---|---|---|---|---|---|
| Niigata | 48 | 77 | 20 | 32 | 42 | | Yes |
| Saitama | 73 | 76 | | 12 | 21 | | Yes |
| Tokyo | 101 | 141 | | 2 | 1 | 1 | Yes |
| Gunma | | 17 (Joetsu Line) | | | | | |

## Keeping it fresh

New cards come out, hours change and stamps are retired, so a prefecture is never really finished.
- Availability reports and corrections from collectors are applied in each data update (see [CONTRIBUTING.md](../CONTRIBUTING.md#how-the-catalogue-grows)).
- The nationwide lists are checked again about every three months, and new cards are added.
- Each data release says what changed.

## Tools

The scripts in `tools/` build the catalogue and the region packs. To make each new prefecture quick:
- [ ] One script per nationwide list that takes a prefecture code (the manhole card script already works this way)
- [ ] A station-stamp script that takes a railway line and fetches station positions from OpenStreetMap
- [ ] Area definitions for each prefecture (which city belongs to which area)
- [ ] A report of what's missing per prefecture, area and city, used for the checklist issues
