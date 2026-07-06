-- ============================================================
-- QuizArena — jeu de données de démonstration
-- 4 catégories × 15 questions (5 par difficulté) = 60 questions
-- Usage : mysql -u root quizarena < seed.sql
-- Les comptes de démonstration sont créés par bin/create_demo_accounts.php
-- (le hash bcrypt doit être généré par PHP, jamais stocké en clair).
-- ============================================================

USE quizarena;

INSERT INTO categories (id, name, icon_url, color_hex, active) VALUES
    (1, 'Sciences', NULL, '#2E86DE', 1),
    (2, 'Histoire & Géographie', NULL, '#E67E22', 1),
    (3, 'Sport', NULL, '#27AE60', 1),
    (4, 'Arts & Divertissement', NULL, '#8E44AD', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ------------------------------------------------------------
-- Catégorie 1 : Sciences
-- ------------------------------------------------------------
INSERT INTO questions (category_id, text, choices_json, correct_index, difficulty) VALUES
(1, 'Quelle planète est la plus proche du Soleil ?',
 '["Vénus", "Mercure", "Mars", "La Terre"]', 1, 'easy'),
(1, 'Quel est le symbole chimique de l''oxygène ?',
 '["O", "Ox", "Oy", "Om"]', 0, 'easy'),
(1, 'Combien de pattes possède une araignée ?',
 '["6", "10", "8", "12"]', 2, 'easy'),
(1, 'Quel organe pompe le sang dans le corps humain ?',
 '["Le foie", "Les poumons", "Le cerveau", "Le cœur"]', 3, 'easy'),
(1, 'À quelle température l''eau bout-elle au niveau de la mer ?',
 '["90 °C", "100 °C", "110 °C", "120 °C"]', 1, 'easy'),
(1, 'Quel gaz les plantes absorbent-elles pour la photosynthèse ?',
 '["Le dioxyde de carbone", "L''oxygène", "L''azote", "L''hydrogène"]', 0, 'medium'),
(1, 'Quelle est la vitesse approximative de la lumière dans le vide ?',
 '["30 000 km/s", "150 000 km/s", "300 000 km/s", "3 000 000 km/s"]', 2, 'medium'),
(1, 'Quel scientifique a formulé la théorie de la relativité générale ?',
 '["Isaac Newton", "Albert Einstein", "Galilée", "Niels Bohr"]', 1, 'medium'),
(1, 'Quelle est la plus grande planète du système solaire ?',
 '["Saturne", "Neptune", "Uranus", "Jupiter"]', 3, 'medium'),
(1, 'Quel élément chimique porte le numéro atomique 1 ?',
 '["L''hélium", "L''hydrogène", "Le lithium", "Le carbone"]', 1, 'medium'),
(1, 'Quelle particule est échangée lors de l''interaction électromagnétique ?',
 '["Le photon", "Le gluon", "Le boson W", "Le neutrino"]', 0, 'hard'),
(1, 'Quel est l''os le plus long du corps humain ?',
 '["L''humérus", "Le tibia", "Le fémur", "Le radius"]', 2, 'hard'),
(1, 'Quelle est la valeur approchée du nombre d''Avogadro ?',
 '["3,14 × 10²³", "6,02 × 10²³", "6,02 × 10²⁶", "1,60 × 10⁻¹⁹"]', 1, 'hard'),
(1, 'Qui a découvert la radioactivité naturelle en 1896 ?',
 '["Marie Curie", "Ernest Rutherford", "Henri Becquerel", "Pierre Curie"]', 2, 'hard'),
(1, 'Dans l''ADN, quelle base s''apparie avec l''adénine ?',
 '["La cytosine", "La guanine", "L''uracile", "La thymine"]', 3, 'hard');

-- ------------------------------------------------------------
-- Catégorie 2 : Histoire & Géographie
-- ------------------------------------------------------------
INSERT INTO questions (category_id, text, choices_json, correct_index, difficulty) VALUES
(2, 'Quelle est la capitale de l''Italie ?',
 '["Milan", "Venise", "Rome", "Naples"]', 2, 'easy'),
(2, 'Sur quel continent se trouve l''Égypte ?',
 '["En Asie", "En Afrique", "En Europe", "En Océanie"]', 1, 'easy'),
(2, 'Quel océan borde la côte ouest de la France ?',
 '["L''océan Atlantique", "L''océan Pacifique", "L''océan Indien", "L''océan Arctique"]', 0, 'easy'),
(2, 'En quelle année a eu lieu la prise de la Bastille ?',
 '["1815", "1789", "1848", "1914"]', 1, 'easy'),
(2, 'Quel pays a la forme d''une botte sur les cartes ?',
 '["L''Espagne", "La Grèce", "Le Portugal", "L''Italie"]', 3, 'easy'),
(2, 'Qui fut le premier président de la Cinquième République française ?',
 '["Georges Pompidou", "Charles de Gaulle", "René Coty", "François Mitterrand"]', 1, 'medium'),
(2, 'Quel est le plus long fleuve de France ?',
 '["La Seine", "Le Rhône", "La Loire", "La Garonne"]', 2, 'medium'),
(2, 'En quelle année la Seconde Guerre mondiale a-t-elle pris fin ?',
 '["1943", "1944", "1945", "1946"]', 2, 'medium'),
(2, 'Quelle mer sépare l''Europe de l''Afrique ?',
 '["La mer Méditerranée", "La mer Noire", "La mer Rouge", "La mer Baltique"]', 0, 'medium'),
(2, 'Quel pays a pour capitale Canberra ?',
 '["La Nouvelle-Zélande", "Le Canada", "L''Australie", "L''Afrique du Sud"]', 2, 'medium'),
(2, 'En quelle année Christophe Colomb a-t-il atteint les Amériques ?',
 '["1453", "1492", "1519", "1503"]', 1, 'hard'),
(2, 'Quel traité signé en 1919 met fin à la Première Guerre mondiale avec l''Allemagne ?',
 '["Le traité de Vienne", "Le traité de Rome", "Le traité de Versailles", "Le traité de Tordesillas"]', 2, 'hard'),
(2, 'Quelle est la capitale du Kazakhstan ?',
 '["Almaty", "Astana", "Tachkent", "Bichkek"]', 1, 'hard'),
(2, 'Quel empereur romain a légalisé le christianisme par l''édit de Milan (313) ?',
 '["Constantin", "Néron", "Auguste", "Trajan"]', 0, 'hard'),
(2, 'Quel détroit sépare l''Asie de l''Amérique ?',
 '["Le détroit de Gibraltar", "Le détroit de Magellan", "Le détroit d''Ormuz", "Le détroit de Béring"]', 3, 'hard');

-- ------------------------------------------------------------
-- Catégorie 3 : Sport
-- ------------------------------------------------------------
INSERT INTO questions (category_id, text, choices_json, correct_index, difficulty) VALUES
(3, 'Combien de joueurs d''une équipe de football sont sur le terrain ?',
 '["9", "10", "11", "12"]', 2, 'easy'),
(3, 'Dans quel sport utilise-t-on une raquette et un volant ?',
 '["Le tennis", "Le badminton", "Le squash", "Le tennis de table"]', 1, 'easy'),
(3, 'Tous les combien d''années ont lieu les Jeux olympiques d''été ?',
 '["Tous les 2 ans", "Tous les 3 ans", "Tous les 5 ans", "Tous les 4 ans"]', 3, 'easy'),
(3, 'De quelle couleur est le maillot du leader du Tour de France ?',
 '["Jaune", "Vert", "Blanc", "À pois rouges"]', 0, 'easy'),
(3, 'Dans quel sport marque-t-on des paniers ?',
 '["Le volley-ball", "Le handball", "Le basket-ball", "Le rugby"]', 2, 'easy'),
(3, 'Combien de points vaut un essai au rugby à XV ?',
 '["3 points", "4 points", "5 points", "7 points"]', 2, 'medium'),
(3, 'Quel pays a remporté la Coupe du monde de football 2018 ?',
 '["Le Brésil", "La France", "L''Allemagne", "La Croatie"]', 1, 'medium'),
(3, 'Sur quelle surface se joue le tournoi de Roland-Garros ?',
 '["Sur gazon", "Sur surface dure", "Sur terre battue", "Sur moquette"]', 2, 'medium'),
(3, 'Quelle est la distance officielle d''un marathon ?',
 '["40,195 km", "41,195 km", "42,195 km", "44,195 km"]', 2, 'medium'),
(3, 'Dans quelle ville les Jeux olympiques d''été 2016 ont-ils eu lieu ?',
 '["Pékin", "Rio de Janeiro", "Londres", "Tokyo"]', 1, 'medium'),
(3, 'Combien de fois Rafael Nadal a-t-il remporté Roland-Garros ?',
 '["12", "13", "14", "15"]', 2, 'hard'),
(3, 'Quel boxeur était surnommé « The Greatest » ?',
 '["Mike Tyson", "Joe Frazier", "George Foreman", "Mohamed Ali"]', 3, 'hard'),
(3, 'En quelle année la France a-t-elle remporté sa première Coupe du monde de football ?',
 '["1984", "1998", "2000", "2006"]', 1, 'hard'),
(3, 'Quel nageur détient le record de médailles d''or olympiques ?',
 '["Michael Phelps", "Ian Thorpe", "Mark Spitz", "Caeleb Dressel"]', 0, 'hard'),
(3, 'Au tennis de table, en combien de points se joue une manche ?',
 '["11 points", "15 points", "21 points", "25 points"]', 0, 'hard');

-- ------------------------------------------------------------
-- Catégorie 4 : Arts & Divertissement
-- ------------------------------------------------------------
INSERT INTO questions (category_id, text, choices_json, correct_index, difficulty) VALUES
(4, 'Qui a peint « La Joconde » ?',
 '["Pablo Picasso", "Léonard de Vinci", "Vincent van Gogh", "Claude Monet"]', 1, 'easy'),
(4, 'Combien de cordes possède une guitare classique ?',
 '["4", "5", "6", "7"]', 2, 'easy'),
(4, 'Quel studio a créé le personnage de Mickey Mouse ?',
 '["Warner Bros", "Pixar", "Disney", "DreamWorks"]', 2, 'easy'),
(4, 'Dans le conte, combien de nains accompagnent Blanche-Neige ?',
 '["5", "6", "8", "7"]', 3, 'easy'),
(4, 'Quel instrument de musique possède 88 touches ?',
 '["Le piano", "L''orgue", "L''accordéon", "Le clavecin"]', 0, 'easy'),
(4, 'Qui a écrit « Les Misérables » ?',
 '["Émile Zola", "Honoré de Balzac", "Victor Hugo", "Gustave Flaubert"]', 2, 'medium'),
(4, 'Quel peintre s''est tranché une partie de l''oreille ?',
 '["Paul Gauguin", "Vincent van Gogh", "Paul Cézanne", "Auguste Renoir"]', 1, 'medium'),
(4, 'Quelle saga littéraire met en scène Harry, Ron et Hermione ?',
 '["Le Seigneur des anneaux", "Narnia", "Percy Jackson", "Harry Potter"]', 3, 'medium'),
(4, 'Quel compositeur a continué à écrire de la musique en devenant sourd ?',
 '["Wolfgang Amadeus Mozart", "Ludwig van Beethoven", "Jean-Sébastien Bach", "Frédéric Chopin"]', 1, 'medium'),
(4, 'Quel film de James Cameron raconte le naufrage d''un paquebot en 1912 ?',
 '["Titanic", "Avatar", "Abyss", "Aliens"]', 0, 'medium'),
(4, 'À quel mouvement artistique associe-t-on Salvador Dalí ?',
 '["Le cubisme", "L''impressionnisme", "Le surréalisme", "Le fauvisme"]', 2, 'hard'),
(4, 'Qui a composé « Les Quatre Saisons » ?',
 '["Jean-Sébastien Bach", "Antonio Vivaldi", "Georg Friedrich Haendel", "Georg Philipp Telemann"]', 1, 'hard'),
(4, 'Quel écrivain français a reçu le prix Nobel de littérature en 1957 ?',
 '["Jean-Paul Sartre", "André Gide", "Albert Camus", "François Mauriac"]', 2, 'hard'),
(4, 'Quel réalisateur a signé « Le Voyage de Chihiro » ?',
 '["Isao Takahata", "Hayao Miyazaki", "Akira Kurosawa", "Makoto Shinkai"]', 1, 'hard'),
(4, 'Dans quel musée est exposée « La Nuit étoilée » de Van Gogh ?',
 '["Au Louvre", "Au musée d''Orsay", "À la National Gallery", "Au MoMA de New York"]', 3, 'hard');
