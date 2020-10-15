alter table merchant_usebutton drop column experimental;
alter table merchant_usebutton drop column urls;
alter table merchant_usebutton drop column metadata;
alter table merchant_usebutton add column homepage_url text;
alter table merchant_usebutton add column toc_url text;
alter table merchant_usebutton add column icon_url text;
alter table merchant_usebutton add column logo_url text;
alter table merchant_usebutton add column description text;